package com.mastfrog.http.testapp.endpoints;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * A pojo, because we like pojos.
 *
 * @author Tim Boudreau
 */
public class SomeObject {

    public int value;
    public String text;

    @JsonCreator
    public SomeObject(@JsonProperty(value = "value") int value, @JsonProperty(value = "text") String text) {
        this.value = value;
        this.text = text;
    }

    public SomeObject incremented() {
        return new SomeObject(value + 1, text + "-xx");
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + this.value;
        hash = 59 * hash + Objects.hashCode(this.text);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SomeObject other = (SomeObject) obj;
        if (this.value != other.value) {
            return false;
        }
        return Objects.equals(this.text, other.text);
    }

    @Override
    public String toString() {
        return text + ":" + value;
    }

}
