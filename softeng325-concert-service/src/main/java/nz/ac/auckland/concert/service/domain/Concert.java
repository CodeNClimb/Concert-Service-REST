package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.common.types.Genre;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "CONCERTS")
public class Concert {

    @Id
    @GeneratedValue
    @Column(name = "")
    private long id;

    private Genre genre;

    private String imageName;

    private Set<Performer> performers;

}
