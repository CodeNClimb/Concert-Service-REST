package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.service.domain.Concert;

import javax.persistence.EntityManager;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

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
