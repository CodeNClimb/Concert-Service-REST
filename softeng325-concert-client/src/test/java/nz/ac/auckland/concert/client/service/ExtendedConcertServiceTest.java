package nz.ac.auckland.concert.client.service;

import nz.ac.auckland.concert.client.clientApp.Subscription;
import nz.ac.auckland.concert.common.dto.ConcertDTO;
import nz.ac.auckland.concert.common.dto.PerformerDTO;
import nz.ac.auckland.concert.common.dto.UserDTO;
import nz.ac.auckland.concert.common.types.PriceBand;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.fail;

/**
 * Test class for testing extended "optional" functionality found in ExtendedService sub-class.
 */
public class ExtendedConcertServiceTest {

    private static Logger _logger = LoggerFactory
            .getLogger(ConcertServiceTest.class);

    private static final int SERVER_PORT = 10000;
    private static final String WEB_SERVICE_CLASS_NAME = ConcertApplication.class.getName();

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
    public void testPerformerSubscription() throws InterruptedException {
        try {
            UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
            _service.createUser(userDTO);

            Subscription subscription = new Subscription();
            Thread thread = new Thread(() -> _service.subscribeToNewPerformers(subscription));
            thread.start();

            ExtendedService service = new ExtendedService();
            UserDTO userDTO2 = new UserDTO("Bulldog1", "123", "Churchill", "Winston");
            service.createUser(userDTO2);

            PerformerDTO newPerformer = new PerformerDTO(null, "Tyga", null, null, new HashSet<>());
            service.createPerformer(newPerformer);

            Thread.sleep(500); // Ensure subscription object is updated

            Assert.assertTrue(subscription.isUnreadNotification());
            Assert.assertEquals(1, subscription.getSubscription().size());
            Assert.assertTrue(subscription.getSubscription().get(0).contains("Tyga"));
            System.out.println(subscription.getSubscription());

            PerformerDTO newPerformer2 = new PerformerDTO(null, "Ravid Aharon", null, null, new HashSet<>());
            service.createPerformer(newPerformer2);

            Thread.sleep(500); // Ensure subscription object is updated

            Assert.assertTrue(subscription.isUnreadNotification());
            Assert.assertEquals(1, subscription.getSubscription().size());
            Assert.assertTrue(subscription.getSubscription().get(0).contains("Ravid Aharon"));
            System.out.println(subscription.getSubscription());

            PerformerDTO newPerformer3 = new PerformerDTO(null, "Stars of the Lid", null, null, new HashSet<>());
            service.createPerformer(newPerformer3);

            Thread.sleep(500); // Ensure subscription object is updated

            Assert.assertTrue(subscription.isUnreadNotification());
            Assert.assertEquals(1, subscription.getSubscription().size());
            Assert.assertTrue(subscription.getSubscription().get(0).contains("Stars of the Lid"));
            System.out.println(subscription.getSubscription());

            PerformerDTO newPerformer4 = new PerformerDTO(null, "Aphex Twin", null, null, new HashSet<>());
            service.createPerformer(newPerformer4);

            Thread.sleep(500); // Ensure subscription object is updated

            Assert.assertTrue(subscription.isUnreadNotification());
            Assert.assertEquals(1, subscription.getSubscription().size());
            Assert.assertTrue(subscription.getSubscription().get(0).contains("Aphex Twin"));
            System.out.println(subscription.getSubscription());

        } catch(ServiceException e) {
            fail();
        }
    }

    @Test
    public void testConcertSubscription() throws InterruptedException {
        try {
            UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
            _service.createUser(userDTO);

            Subscription subscription = new Subscription();
            Thread thread = new Thread(() -> _service.subscribeToNewConcerts(subscription));
            thread.start();

            ExtendedService service = new ExtendedService();
            UserDTO userDTO2 = new UserDTO("Bulldog1", "123", "Churchill", "Winston");
            service.createUser(userDTO2);

            Set<Long> performers = new HashSet<>();
            performers.add(1L);
            performers.add(2L);
            Set<LocalDateTime> dates = new HashSet<>();
            dates.add(LocalDateTime.now());
            Map<PriceBand, BigDecimal> prices = new HashMap<>();
            prices.put(PriceBand.PriceBandA, new BigDecimal(3));
            prices.put(PriceBand.PriceBandB, new BigDecimal(3));
            prices.put(PriceBand.PriceBandC, new BigDecimal(3));


            ConcertDTO concertDTO = new ConcertDTO(
                    21L,
                    "Heres a test!",
                    dates,
                    prices,
                    performers);
            service.createConcert(concertDTO);

            Thread.sleep(500); // Ensure subscription object is updated

            Assert.assertTrue(subscription.isUnreadNotification());
            Assert.assertEquals(1, subscription.getSubscription().size());
            Assert.assertTrue(subscription.getSubscription().get(0).contains("Heres a test!"));
            System.out.println(subscription.getSubscription());

        } catch(ServiceException e) {
            fail();
        }
    }

    @Test
    public void testSubscribeToNewImages() throws InterruptedException {
        try {
            UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
            _service.createUser(userDTO);

            Subscription subscription = new Subscription();
            Thread thread = new Thread(() -> _service.subscribeToNewImages(subscription));
            thread.start();

            ExtendedService service = new ExtendedService();
            UserDTO userDTO2 = new UserDTO("Bulldog1", "123", "Churchill", "Winston");
            service.createUser(userDTO2);

            PerformerDTO newPerformer = new PerformerDTO(1L, null, "test.jpg", null, new HashSet<>());
            service.addImage(newPerformer);

            Thread.sleep(500); // Ensure subscription object is updated

            Assert.assertTrue(subscription.isUnreadNotification());
            Assert.assertEquals(1, subscription.getSubscription().size());
            Assert.assertTrue(subscription.getSubscription().get(0).contains("test.jpg"));
            System.out.println(subscription.getSubscription());

        } catch(ServiceException e) {
            fail();
        }
    }

    @Test
    public void testSubscribeToNewImagesWithPerformer() throws InterruptedException {
        try {
            UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
            _service.createUser(userDTO);

            Subscription subscription = new Subscription();
            Thread thread = new Thread(() -> {
                PerformerDTO performerDTO = new PerformerDTO(1L, null, null, null, new HashSet<>());
                _service.subscribeToNewImagesForPerformer(performerDTO, subscription);
            });
            thread.start();

            ExtendedService service = new ExtendedService();
            UserDTO userDTO2 = new UserDTO("Bulldog1", "123", "Churchill", "Winston");
            service.createUser(userDTO2);

            PerformerDTO newPerformer = new PerformerDTO(1L, null, "test.jpg", null, new HashSet<>());
            service.addImage(newPerformer);

            Thread.sleep(500); // Ensure subscription object is updated

            Assert.assertTrue(subscription.isUnreadNotification());
            Assert.assertEquals(1, subscription.getSubscription().size());
            Assert.assertTrue(subscription.getSubscription().get(0).contains("test.jpg"));
            System.out.println(subscription.getSubscription());

        } catch(ServiceException e) {
            fail();
        }
    }
}
