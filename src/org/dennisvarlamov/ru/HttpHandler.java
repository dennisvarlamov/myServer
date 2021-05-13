package org.dennisvarlamov.ru;

public interface HttpHandler {
    String handle(HttpRequest request, HttpResponse response);
}
