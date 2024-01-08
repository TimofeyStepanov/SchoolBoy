package ru.stepanoff;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@SpringBootApplication
public class Main extends Application {
    public static void main(String[] args) {
        Application.launch();
    }

    private ConfigurableApplicationContext springContext;
    private Parent rootNode;

    @Override
    public void init() throws IOException {
        springContext = SpringApplication.run(Main.class);
        FXMLLoader fxmlLoader = new FXMLLoader(ClassLoader.getSystemClassLoader().getResource("cursa4.fxml"));
        fxmlLoader.setControllerFactory(springContext::getBean);

        rootNode = fxmlLoader.load();
    }


    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(rootNode, 800, 600);

        stage.setTitle("Курсовая");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    @Override
    public void stop() {
        springContext.stop();
    }
}
