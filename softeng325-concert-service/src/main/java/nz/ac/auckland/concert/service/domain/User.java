package nz.ac.auckland.concert.service.domain;

import javax.persistence.*;

@Entity
@Table(name = "USERS")
public class User {


    @Id
    @GeneratedValue
    private long id;


    @Column(name = "FIRST_NAME")
    private String firstName;


    @Column(name = "LAST_NAME")
    private String lastName;


    @Column(name = "USERNAME")
    private String username;


    @Column(name = "PASSWORD")
    private String password;


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
