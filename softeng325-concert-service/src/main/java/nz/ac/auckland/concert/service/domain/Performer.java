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

    public Performer(Long id) {
        this.id = id;
    }

    public Performer(String name, String imageName, Genre genre, Set<Concert> concerts) {
        this.name = name;
        this.imageName = imageName;
        this.genre = genre;
        this.concerts = concerts;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
        return concerts  == null ? new HashSet<>() : concerts.stream().map(Concert::getId).collect(Collectors.toSet());
    }
}
