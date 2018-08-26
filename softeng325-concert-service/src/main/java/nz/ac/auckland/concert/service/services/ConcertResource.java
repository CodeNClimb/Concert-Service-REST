package nz.ac.auckland.concert.service.services;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/concerts")
public class ConcertResource {

    private final PersistenceManager _pm;

    public ConcertResource() {

        _pm = PersistenceManager.instance();

    }


    @GET
    @Produces(MediaType.APPLICATION_XML)
    public Response getConcerts() {



        System.out.println("CVYFICDYFIKCDUOCFGUCUOCGUICUICYUICGF");
        return Response.status(204).build();
    }

}
