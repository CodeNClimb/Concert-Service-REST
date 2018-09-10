package nz.ac.auckland.concert.common.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "news-item")
@XmlAccessorType(XmlAccessType.FIELD)
public class NewsItemDTO {

    public NewsItemDTO() {}

    public NewsItemDTO(String cookie, List<String> notification) {
        this.cookie = cookie;
        this.notification = notification;
    }

    public NewsItemDTO(String cookie, String notification) {
        this.cookie = cookie;
        this.notification = new ArrayList<>();
        this.notification.add(notification);
    }

    @XmlElement(name = "cookie")
    private String cookie;

    @XmlElement(name = "notification")
    private List<String> notification;

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public List<String> getNotification() {
        return notification;
    }

    public void setNotification(List<String> notification) {
        this.notification = notification;
    }
}
