package nz.ac.auckland.concert.service.domain;

import javax.persistence.*;
import java.util.Set;

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
    private String genre;


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

    public String getGenre() {
        return genre;
    }

    public Set<Concert> getConcerts() {
        return concerts;
    }
}
