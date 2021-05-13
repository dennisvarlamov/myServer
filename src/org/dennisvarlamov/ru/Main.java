package org.dennisvarlamov.ru;

public class Main {
    public static void main(String[] args) {
        new Server((request, response) -> {
            return  "<html><body><h1>Hello, myServer</h1>It handler</body></html>";
        }).initialization();
    }
}

