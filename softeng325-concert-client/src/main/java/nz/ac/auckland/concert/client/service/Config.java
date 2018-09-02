package nz.ac.auckland.concert.client.service;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class Config {

    // The following macros are for a thread safe pooled client.
    public static final PoolingHttpClientConnectionManager CM = new PoolingHttpClientConnectionManager();
    public static final CloseableHttpClient CLOSEABLE_HTTP_CLIENT = HttpClientBuilder.create().setConnectionManager(CM).build();
    public static final ApacheHttpClient4Engine ENGINE = new ApacheHttpClient4Engine(CLOSEABLE_HTTP_CLIENT);
    public final static Client POOLED_CLIENT = new ResteasyClientBuilder().httpEngine(ENGINE).build();

    // DEFAULT_CLIENT should not be used in a multi threaded environment.
    public static final Client DEFAULT_CLIENT = ClientBuilder.newClient();

    public static final String LOCAL_SERVER_ADDRESS = "http://localhost:10000/services";

    // AWS S3 access credentials for concert images.
    public static final String AWS_ACCESS_KEY_ID = "AKIAJOG7SJ36SFVZNJMQ";
    public static final String AWS_SECRET_ACCESS_KEY = "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";

    // Name of the S3 bucket that stores images.
    public  static final String AWS_BUCKET = "concert2.aucklanduni.ac.nz";

}
