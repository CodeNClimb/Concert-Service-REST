package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.common.types.Genre;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "PERFORMERS")
public class Performer  {

    public Performer() {}


    @Id
    @GeneratedValue
    @Column(name = "ID")
    private long id;


    @Column(name = "NAME")
    private String name;


    @Column(name = "IMAGE_NAME")
    private String imageName;


    @Column(name = "GENRE")
    @Enumerated(EnumType.STRING)
    private Genre genre;


    @ManyToMany(mappedBy = "performers")
    private Set<Concert> concerts;


    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }


    public String getImageName() {
        return imageName;
    }

    public Genre getGenre() {
        return genre;
    }

    public Set<Concert> getConcerts() {
        return concerts;
    }

    public Set<Long> getConcertIds() {
        return concerts.stream().map(Concert::getId).collect(Collectors.toSet());
    }
}
