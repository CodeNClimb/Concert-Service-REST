package nz.ac.auckland.concert.client.service;

import nz.ac.auckland.concert.common.dto.PerformerDTO;
import nz.ac.auckland.concert.common.dto.UserDTO;
import nz.ac.auckland.concert.service.services.ConcertApplication;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.fail;

public class ExtendedConcertServiceTest {

    private static Logger _logger = LoggerFactory
            .getLogger(ConcertServiceTest.class);

    private static final int SERVER_PORT = 10000;
    private static final String WEB_SERVICE_CLASS_NAME = ConcertApplication.class.getName();
    private static final int RESERVATION_EXPIRY_TIME_IN_SECONDS = 5;

    private static Client _client;
    private static Server _server;

    private ExtendedService _service;

    @BeforeClass
    public static void createClientAndServer() throws Exception {
        // Use ClientBuilder to create a new client that can be used to create
        // connections to the Web service.
        _client = ClientBuilder.newClient();

        // Start the embedded servlet container and host the Web service.
        ServletHolder servletHolder = new ServletHolder(new HttpServletDispatcher());
        servletHolder.setInitParameter("javax.ws.rs.Application", WEB_SERVICE_CLASS_NAME);
        ServletContextHandler servletCtxHandler = new ServletContextHandler();
        servletCtxHandler.setContextPath("/services");
        servletCtxHandler.addServlet(servletHolder, "/");
        _server = new Server(SERVER_PORT);
        _server.setHandler(servletCtxHandler);
    }

    @AfterClass
    public static void shutDown() {
        _client.close();
    }

    @Before
    public void startServer() throws Exception {
        _server.start();
        _service = new ExtendedService();
    }

    @After
    public void stopServer() throws Exception {
        _server.stop();
    }

    @Test
    public void testCreatePerformer() {
        try {
            UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
            _service.createUser(userDTO);

            PerformerDTO performerDTO = new PerformerDTO(null, "Soulja Boy", null, null, new HashSet<>());
            PerformerDTO returnedPerformerDTO = _service.createPerformer(performerDTO);
            Assert.assertNotNull(returnedPerformerDTO.getId());

            Set<PerformerDTO> performerDTOS = _service.getPerformers();
            Assert.assertEquals(21, performerDTOS.size());
        } catch(ServiceException e) {
            fail();
        }
    }

    @Test
    public void testPerformerSubscription() {
        try {
            UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
            _service.createUser(userDTO);


            Thread thread = new Thread(() -> {
                String response = _service.subscribeToNewPerformers();
                System.out.println(response);
            });
            thread.start();

            ExtendedService service = new ExtendedService();
            UserDTO userDTO2 = new UserDTO("Bulldog1", "123", "Churchill", "Winston");
            service.createUser(userDTO2);

            PerformerDTO newPerformer = new PerformerDTO(null, "Tyga", null, null, null);
            service.createPerformer(newPerformer);


        } catch(ServiceException e) {
            fail();
        }
    }

}
