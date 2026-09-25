package ru.olgak.folks.soap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {"folk"})
@XmlRootElement(name = "folkResponse")
public class FolkResponse {

    private SearchResponse.Folk folk;

    public SearchResponse.Folk getFolk() {
        return folk;
    }

    public void setFolk(SearchResponse.Folk folk) {
        this.folk = folk;
    }
}
