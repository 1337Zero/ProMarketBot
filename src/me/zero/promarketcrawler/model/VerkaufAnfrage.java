package me.zero.promarketcrawler.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerkaufAnfrage {

	private String aktie = null;
	private double verkaufpreis = 0.0;
	private boolean fullfilled = false;
	private int amount = 0;

	public VerkaufAnfrage(String aktie,double verkaufPreis,int amount) {
		this.aktie = aktie;
		this.verkaufpreis = verkaufPreis;
		this.amount = amount;
	}


	public void updatePrice() {

	}
	public boolean publish(String cookie) {
        try {
    		URL url = new URL("https://pr0gramm.com/api/stocks/sell");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setDoInput(true);
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			con.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36");
			con.setRequestProperty("cookie", cookie);
			con.getOutputStream().write(createRawRequestData("amount=" + amount + "&price=" + formatPrice(verkaufpreis) + "%2C00&symbol=" + aktie + "&_nonce=6509fa52c36016c1"));

			ByteArrayOutputStream result = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
            int length;
			con.getInputStream().read(buffer);
			while ((length = con.getInputStream().read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
			return con.getResponseCode() == 200;
        } catch (IOException e) {
			e.printStackTrace();
		}
        return false;
	}

	private String formatPrice(double price) {
		return ("" + price).replace(".", "%2C");
	}

	private byte[] createRawRequestData(String request) {
		try {
			return request.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return new byte[0];
	}

	public void cancel() {

	}


	public boolean isFullfilled() {
		return fullfilled;
	}


	public void setFullfilled(boolean fullfilled) {
		this.fullfilled = fullfilled;
	}


	public String getAktie() {
		return aktie;
	}


	public double getVerkaufpreis() {
		return verkaufpreis;
	}


	public int getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		return "VerkaufAnfrage [aktie=" + aktie + ", verkaufpreis=" + verkaufpreis + ", fullfilled=" + fullfilled
				+ ", amount=" + amount + "]";
	}
}
