package org.dennisvarlamov.ru;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class Server {
    // Задаем емкость нашего буфера
    private final static int BUFFER_SIZE = 256;
    private AsynchronousServerSocketChannel server;

    private final HttpHandler handler;


    Server(HttpHandler handler) {
        this.handler = handler;
    }

    public void initialization() { // Сервер начинает слушать сеть
        try {
            // Начинаем слушать сеть
            // AsynchronousServerSocketChannel - Асинхронный канал для потоковых сокетов прослушивания.
            server = AsynchronousServerSocketChannel.open();
            // Связываем сокет канала с локальным адресом и настраиваем сокет на прослушивание подключений
            // InetSocketAddress - создает адрес сокета по имени хоста и порта
            server.bind(new InetSocketAddress("127.0.0.1", 8088));

            while (true) {
                // Сервер принимает соединение (тут мы получаем запрос от клиента)
                // future - будет возвращать какое-то соединение
                // Future - представляет результат асинхронного вычисления. Предоставляются методы, чтобы проверить, завершено ли вычисление, дождаться его завершения и получить результат вычисления. Результат может быть получен с помощью метода * {@code get} только после завершения вычисления, при необходимости блокируясь, пока он не будет готов.
                // accept - открывает сокет и дожидается клиента
                Future<AsynchronousSocketChannel> future = server.accept();
                handleClient(future);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Future<AsynchronousSocketChannel> future) throws InterruptedException, ExecutionException, TimeoutException, IOException {
        System.out.println("new client connection");

        AsynchronousSocketChannel clientChannel = future.get();

        while (clientChannel != null && clientChannel.isOpen()) {
            // Создаем буфер и аллоцируем его (положение буфера нулевое, емкость BUFFER_SIZE, то есть 256 символов)
            // В buffer буду записывать всю информацию
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            // Использую builder чтобы собрать все то, что хочет сказать браузер :)
            StringBuilder builder = new StringBuilder();
            // Переменная, которая используется для того, чтобы проверить всю ли информацию мы собрали из браузера
            boolean keepReading = true;

            while (keepReading) {
                // Читаем, то что прислал клиент
                int readResult = clientChannel.read(buffer).get();

                // Тут должна быть проверка, на то что сообщение заканчивается 2-мя переводами строк, но для упрощения я этим принебрег

                keepReading = readResult == BUFFER_SIZE;
                buffer.flip();

                CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);

                // Добавляем в builder информацию из буфера
                builder.append(charBuffer);
                // Сбрасываем позицию курсора на ноль (буфер становится таким же каким был при инициализации, но все данные в нем остаются)
                buffer.clear();
            }

            HttpRequest request = new HttpRequest(builder.toString());
            HttpResponse resp = new HttpResponse();

            if (handler != null) {
                try {
                    String body = this.handler.handle(request, resp);

                    if (body != null && !body.isBlank()) {

                        if (resp.getHeaders().get("Content-Type") == null) {
                            resp.addHeader("Content-Type", "text/html; charset=utf-8");
                        }
                        resp.setBody(body);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    resp.setStatusCode(500);
                    resp.setStatus("Internal server error");
                    resp.addHeader("Content-Type", "text/html; charset=utf-8");
                    resp.setBody("<html><body><h1>Error happens</h1></body></html>");
                }
            } else {
                resp.setStatusCode(404);
                resp.setStatus("Not found");
                resp.addHeader("Content-Type", "text/html; charset=utf-8");
                resp.setBody("<html><body><h1>Resource not found</h1></body></html>");
            }

            ByteBuffer response = ByteBuffer.wrap(resp.getBytes());
            // Отвечаем клиенту
            clientChannel.write(response);

            // Закрываем соединение
            clientChannel.close();
        }
    }
}
