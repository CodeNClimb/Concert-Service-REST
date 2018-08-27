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

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public Set<Concert> getConcerts() {
        return concerts;
    }

    public void setConcerts(Set<Concert> concerts) {
        this.concerts = concerts;
    }
}
