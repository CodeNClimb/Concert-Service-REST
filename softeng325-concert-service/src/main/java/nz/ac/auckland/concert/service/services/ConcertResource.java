package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.common.dto.ConcertDTO;
import nz.ac.auckland.concert.service.domain.Concert;
import nz.ac.auckland.concert.service.domain.Mappers.ConcertMapper;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/concerts")
public class ConcertResource {

    private final PersistenceManager _pm;

    public ConcertResource() {

        _pm = PersistenceManager.instance();

    }


    @GET
    @Produces(MediaType.APPLICATION_XML)
    public Response getConcerts() {

        EntityManager em = _pm.createEntityManager();

        try {
            EntityTransaction tx = em.getTransaction();
            tx.begin();

            TypedQuery<Concert> q = em.createQuery("SELECT c FROM Concert c", Concert.class);
            List<Concert> concerts = q.getResultList();

            tx.commit();

            List<ConcertDTO> concertDTOs = concerts.stream().map(ConcertMapper::doDto).collect(Collectors.toList());
            GenericEntity<List<ConcertDTO>> entity = new GenericEntity<List<ConcertDTO>>(concertDTOs) {};

            return Response
                    .status(Response.Status.OK)
                    .entity(entity)
                    .build();
        } finally {
            em.close();
        }
    }

}
