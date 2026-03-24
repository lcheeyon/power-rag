package com.example;

/**
 * Sample class used in parser unit tests.
 */
public class Sample {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String greet(String target) {
        return "Hello, " + target + "!";
    }
}
