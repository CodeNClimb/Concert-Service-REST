package nz.ac.auckland.concert.client.service;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class Config {

    public static final Client DEFAULT_CLIENT = ClientBuilder.newClient();

    public static final String LOCAL_SERVER_ADDRESS = "http://localhost:10000/services";

}
