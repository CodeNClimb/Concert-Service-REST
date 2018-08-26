package nz.ac.auckland.concert.service.services;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

@ApplicationPath("/services")
public class ConcertApplication extends Application {

    private Set<Object> _singletons = new HashSet<>();

    public ConcertApplication() {
        _singletons.add(new ConcertResource());
    }

    @Override
    public Set<Object> getSingletons() {
        return _singletons;
    }

    @Override
    public Set<Class<?>> getClasses() {
        return super.getClasses();
    }


}
