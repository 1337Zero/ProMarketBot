import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;

import me.zero.promarketcrawler.model.KaufAnfrage;
import me.zero.promarketcrawler.model.VerkaufAnfrage;

public class ProMarketCrawler {

	private LinkedTreeMap<String, ArrayList<LinkedTreeMap<String, Double>>> log = new LinkedTreeMap<>();

	private HashMap<String,ArrayList<KaufAnfrage>> kaufAnfragen = new HashMap<String,ArrayList<KaufAnfrage>>();
	private HashMap<String,ArrayList<VerkaufAnfrage>> verkaufAnfragen = new HashMap<String,ArrayList<VerkaufAnfrage>>();

	private HashMap<String, HashMap<String, Object>> owning = new HashMap<String,HashMap<String, Object>>();

	private double benisLeft = 0.0;

	private String cookie = "me=HierDeinenCookieEingeben";

	private Gson gson = new Gson();

	private static final Logger logger = Logger.getLogger( ProMarketCrawler.class.getName() );

	public ProMarketCrawler() {
		loadData();
		startThread();
	}

	public static void main(String[] args) {
		new ProMarketCrawler();
	}

	public void startThread() {
		Runnable r = new Runnable() {
			@Override
			public void run() {
				while(!Thread.interrupted()) {
					try {
						fetchFreeBenis(cookie);
						toJson(fetch());
						store();
						Calendar calendar = Calendar.getInstance();
						int timer = 120;
						calendar.add(Calendar.SECOND, timer);
						compareData();
				        Date date =  calendar.getTime();
						System.out.println("next scan will be " + date);
						Thread.sleep(1000*timer);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};

		Thread t = new Thread(r);
		t.start();
	}

	private void compareData() {
		ArrayList<String> actions = new ArrayList<String>();
		for(String key : log.keySet()) {
			System.out.println("----- " + key + " (" + getAmountOfSamples(key) + ") -----");
			double avgPrice = getAveragePrice(key);
			double priceDiff = getPriceAVGDiffrence(key,avgPrice);
			double atmPrice = log.get(key).get(log.get(key).size()-1).get("cur");
			boolean shouldBuy = priceDiff > 0.01;
			boolean shouldSell = priceDiff < -0.01 || priceDiff > 0.01;
			int minBuyAmount = getMinBuyAmount(atmPrice);
			System.out.println("	durchschnitt: " + avgPrice);
			System.out.println("	was letzte Preis: " + atmPrice);
			System.out.println("	richtung : " + (isAboveAVG(key, avgPrice)?"steigend":"fallend"));
			System.out.println("	diffrence to AVG: " + priceDiff + " %");
			System.out.println("	should buy " + (shouldBuy ? "Kaufen":"Nein"));
			System.out.println("	should sell " + (shouldSell ? "Verkaufen":"Nein"));
			if(priceDiff > 0.01)System.out.println("	mindestkaufmenge: " +  minBuyAmount);

			if(shouldBuy) {
				double totalBuyPrice = (atmPrice*minBuyAmount)+2;
				if(benisLeft > totalBuyPrice) {
					if((atmPrice/avgPrice) < 2) {
						int amountToBuy = (int) (round((benisLeft-2.0)/atmPrice, 0));
						KaufAnfrage ka = new KaufAnfrage(key, atmPrice, amountToBuy);
						if(ka.publish(cookie)) {
							ArrayList<KaufAnfrage> anfragen = kaufAnfragen.getOrDefault(key,new ArrayList<KaufAnfrage>());
							anfragen.add(ka);
							kaufAnfragen.put(key, anfragen);
							actions.add("Kaufanfrage gestellt! (" + ka + ")");
						}else {
							actions.add("Fehler beim erstellen der Kaufanfrage! (" + ka + ")");
						}
					}else {
						actions.add("ignoring " + key + " overrated (+" + priceDiff + " %)");
					}
				}else {
					actions.add("\ti would buy " + key + " but i dont have benis (+" + priceDiff + " %)");
				}
			}
			if(shouldSell) {
				//Stoppe alle Kaufangebote falls existent
				//Erstelle Verkaufsangebote
				if(owning.containsKey(key)) {
					HashMap<String, Object> sellAbleStuff = owning.get(key);
					VerkaufAnfrage anfrage = new VerkaufAnfrage(key, atmPrice, ((Double)sellAbleStuff.get("amount")).intValue());
					double umsatz = ( (((Double)sellAbleStuff.get("amount")) * (atmPrice)) - (((Double)sellAbleStuff.get("amount")) * ((Double)sellAbleStuff.get("price"))));
					if(umsatz > 0 && !key.equalsIgnoreCase("KDSE")) {
						ArrayList<VerkaufAnfrage> anfragen = verkaufAnfragen.getOrDefault(key, new ArrayList<VerkaufAnfrage>());
						if(anfrage.publish(cookie)) {
							anfragen.add(anfrage);
							verkaufAnfragen.put(key, anfragen);
							actions.add("Verkaufanfrage gestellt! (" + anfrage + ")");
						}else {
							actions.add("Fehler beim erstellen der Verkaufanfrage! (" + anfrage + ")");
						}
					}else {
						actions.add("\twaiting for " + key + " to reach >= " + sellAbleStuff.get("price") + " (atm: " + atmPrice + ",would be " + umsatz + ")");
					}
				}
			}
		}
		for(String action : actions) {
			System.err.println(action);
		}
	}

	private int getAmountOfSamples(String aktie) {
		return log.get(aktie).size();
	}
	private int getMinBuyAmount(double kaufpreis) {
		//Kaufpreis
		//min 1% gewinn
		//Gewinn > 2 seien um transaktionsgebühren abzufangen
		//Kaufenpreis: 34
		//Vk: min 34,34
		//Min Menge > 6
		int amount = (int) round(2/(kaufpreis*0.01), 0);
		return amount;
	}
	private boolean isAboveAVG(String aktie,double avg) {
		return getPriceAVGDiffrence(aktie, avg) > 0.0;
	}
	private double getPriceAVGDiffrence(String aktie,double avg) {
		double newest = log.get(aktie).get(log.get(aktie).size()-1).get("cur");
		double diffrence = round(newest/avg,4);
		return round(diffrence-1, 4);
	}
	public static double round(double val, int sca) {
		  double s = Math.pow(10, sca);
		  return Math.round(val * s) / s;
		}
	private double getAveragePrice(String aktie) {
		ArrayList<LinkedTreeMap<String, Double>> gsonToKeyData = log.get(aktie);
		int amount = 0;
		double price = 0.0;
		for(LinkedTreeMap<String, Double> s : gsonToKeyData) {
			amount++;
			price += s.get("cur");
		}
		return price/amount;
	}

	private void store() {
		File f = new File("data.json");
		if(!f.exists()) {
			try {
				f.createNewFile();
			} catch (JsonSyntaxException | JsonIOException | FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(f));
			writer.write(gson.toJson(log));
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private void loadData() {
		File f = new File("data.json");
		if(f.exists()) {
			try {
				LinkedTreeMap<String,ArrayList<LinkedTreeMap<String, Double>>> freshData = gson.fromJson(new BufferedReader(new FileReader(f)), LinkedTreeMap.class);
				log = freshData;
			} catch (JsonSyntaxException | JsonIOException | FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	private String fetch() {
		try {
			URL url = new URL("https://pr0gramm.com/api/stocks/prices");

			BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
			StringBuilder builder = new StringBuilder();
			String s = reader.readLine();
			builder.append(s);
			while(s != null) {
				s = reader.readLine();
				if(s != null) {
					builder.append(s);
				}
			}
			reader.close();
			return builder.toString();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

	private void toJson(String json) {
		HashMap<?,?> data = gson.fromJson(json, HashMap.class);
		LinkedTreeMap<String, LinkedTreeMap<String, Double>> prices = (LinkedTreeMap<String, LinkedTreeMap<String, Double>>) data.get("prices");
		for(String aktie : prices.keySet()) {
			//HashMap<String, ArrayList<HashMap<String, Double>>> log
			LinkedTreeMap<String, Double> aktiePriceList = prices.getOrDefault((Object)aktie,new LinkedTreeMap<String, Double>());
			//Hole die Log liste der preise für aktie
			ArrayList<LinkedTreeMap<String, Double>> storedAktienData = log.getOrDefault(aktie,new ArrayList<LinkedTreeMap<String, Double>>());
			storedAktienData.add(aktiePriceList);
			log.put(aktie, storedAktienData);
		}
	}

	public int fetchFreeBenis(String cookie) {
		try {
    		URL url = new URL("https://pr0gramm.com/api/stocks/account");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setDoInput(true);
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			con.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36");
			con.setRequestProperty("cookie", cookie);
			BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
			LinkedTreeMap map = gson.fromJson(reader.readLine(), LinkedTreeMap.class);
			LinkedTreeMap<String, Double> balance = (LinkedTreeMap<String, Double>) map.get("balance");
			benisLeft = balance.get("available");
			ArrayList<LinkedTreeMap<String, Double>> portfolio = (ArrayList<LinkedTreeMap<String, Double>>) map.get("portfolio");
			for(LinkedTreeMap<String, Double> stonk : portfolio) {
				String name = ""+stonk.get("symbol");
				double amount = stonk.get("amount");
				double price = stonk.get("price");
				System.out.println("name: " + name);
				System.out.println("amount: " + amount);
				System.out.println("price: " + price);
				//HashMap<String, HashMap<String, Double>>
				HashMap<String, Object> stack = owning.getOrDefault(portfolio, new HashMap<String,Object>());
				stack.put("name", name);
				stack.put("amount", amount);
				stack.put("price", price);
				owning.put(name, stack);
			}
			reader.close();
        } catch (IOException e) {
			e.printStackTrace();
		}

		return 0;
	}

}
