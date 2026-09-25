package ru.olgak.folks.app;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Запуск встроенного Jetty с SOAP, REST и страницей участников.
 */
public final class FolksApplication {

    private FolksApplication() {
    }

    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
        context.registerShutdownHook();
        System.out.println("Folks started");
        System.out.println("UI    http://localhost:8088/ui/");
        System.out.println("REST  http://localhost:8088/rest/services/folks/v1");
        System.out.println("SOAP  http://localhost:8088/soap/services/folks/v1?wsdl");
        Thread.currentThread().join();
    }
}
