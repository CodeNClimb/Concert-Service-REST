package nz.ac.auckland.concert.client.service;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class Config {

    public static final Client DEFAULT_CLIENT = ClientBuilder.newClient();

    public static final String LOCAL_SERVER_ADDRESS = "http://localhost:10000/services";

    // AWS S3 access credentials for concert images.
    public static final String AWS_ACCESS_KEY_ID = "AKIAJOG7SJ36SFVZNJMQ";
    public static final String AWS_SECRET_ACCESS_KEY = "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";

    // Name of the S3 bucket that stores images.
    public  static final String AWS_BUCKET = "concert2.aucklanduni.ac.nz";

}
