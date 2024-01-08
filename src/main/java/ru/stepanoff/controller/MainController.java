package ru.stepanoff.controller;


import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Data
class Point {
    private final double x;
    private final double y;
}

@Slf4j
@Component
public class MainController {
    private enum Place {
        SCHOOL("В школе"), HOUSE("Отдых дома"),
        PLACE_FOR_HOMEWORK("Дома делаю уроки"), CHESS_HOUSE("В шахматном классе"),
        PLACE_FOR_SLEEP("Поспал"), WALKING_OUT_PLACE("Погулял");

        private final String placeInRussian;

        Place(String placeInRussian) {
            this.placeInRussian = placeInRussian;
        }
    }

    private enum WeekDay {
        SUNDAY(0, "Воскресенье", true),
        MONDAY(1, "Понедельник", false),
        TUESDAY(2, "Вторник", false),
        WEDNESDAY(3, "Среда", false),
        THURSDAY(4, "Четверг", false),
        FRIDAY(5, "Пятница", false),
        SATURDAY(6, "Суббота", true);

        final int dayPosition;
        final boolean dayIsWeekend;
        final String dayNameInRussian;

        WeekDay(int dayPosition, String dayNameInRussian, boolean dayIsWeekend) {
            this.dayPosition = dayPosition;
            this.dayNameInRussian = dayNameInRussian;
            this.dayIsWeekend = dayIsWeekend;
        }

        public WeekDay nextDay() {
            int numberOfDays = WeekDay.values().length;
            int nextDayPosition = (dayPosition + 1) % numberOfDays;
            return WeekDay.values()[nextDayPosition];
        }
    }

    private static final String STYLE_FOR_AVAILABLE_BUTTON = "activePanelButton";
    private static final String STYLE_FOR_UNAVAILABLE_BUTTON = "notActivePanelButton";

    private static final int TIME_TO_WAKE_UP = 8;
    private static final int HOUR_TO_GO_TO_BED = 22;

    private static final String MSG_ABOUT_GO_TO_SLEEP = "Время позднее, надо идти спать";
    private static final String MSG_ABOUT_NOT_GO_TO_SLEEP = "Спать рано";

    private static final int TIME_TO_CHECK_UPDATE = 10 * 1000;

    @FXML
    private Button chillOutButton;

    @FXML
    private Button doSubjectsButton;

    @FXML
    private Button goToChessButton;

    @FXML
    private Button goToSchoolButton;

    @FXML
    private Button sleepButton;

    @FXML
    private Button walkingOutButton;

    @FXML
    private TextField textFieldForInfo;

    @FXML
    private TextField textFieldForDay;

    @FXML
    private TextField textFieldForHour;

    @FXML
    private AnchorPane anchorPane;

    private final ImageView personImageView;

    private final Map<Place, Point> placesAndItsMapPoints = Map.of(
            Place.SCHOOL, new Point(400, 220),
            Place.HOUSE, new Point(595, 240),
            Place.PLACE_FOR_HOMEWORK, new Point(595, 240),
            Place.CHESS_HOUSE, new Point(450, 355),
            Place.PLACE_FOR_SLEEP, new Point(595, 240),
            Place.WALKING_OUT_PLACE, new Point(240, 235)
    );

    private final Map<Place, Integer> placesAndItsHours = Map.of(
            Place.SCHOOL, 6,
            Place.HOUSE, 1,
            Place.PLACE_FOR_HOMEWORK, 3,
            Place.CHESS_HOUSE, 2,
            Place.WALKING_OUT_PLACE, 1
    );

    private Map<Place, Button> placeAndItsButton;

    private final Lock lock = new ReentrantLock();

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    private volatile int currentHour = TIME_TO_WAKE_UP;
    private volatile WeekDay currentDay = WeekDay.MONDAY;

    private volatile long lastUserCommandTime;
    private volatile Place currentPlace;

    private final Set<Place> availablePlaces = new HashSet<>();

    public MainController(
            @Value("${studentImageName}") String personImageName,
            @Value("${personHeight}") int personHeight,
            @Value("${personWidth}") int personWidth) {

        String personImageURL = String.valueOf(ClassLoader.getSystemClassLoader().getResource(personImageName));
        Image image = new Image(personImageURL);

        personImageView = new ImageView(image);
        personImageView.setFitHeight(personHeight);
        personImageView.setFitWidth(personWidth);
    }

    @FXML
    public void initialize() {
        placeAndItsButton = Map.of(
                Place.SCHOOL, goToSchoolButton,
                Place.HOUSE, chillOutButton,
                Place.PLACE_FOR_HOMEWORK, doSubjectsButton,
                Place.CHESS_HOUSE, goToChessButton,
                Place.WALKING_OUT_PLACE, walkingOutButton,
                Place.PLACE_FOR_SLEEP, sleepButton
        );

        //placeAndItsButton.values().forEach(button -> button.getStylesheets().add(getClass().getResource("cursa4.css").toExternalForm()));

        Point housePoint = placesAndItsMapPoints.get(Place.HOUSE);
        personImageView.setX(housePoint.getX());
        personImageView.setY(housePoint.getY());
        anchorPane.getChildren().add(personImageView);

        textFieldForInfo.setText("Выберите команду");
        textFieldForHour.setText(currentHour + ":00");
        textFieldForDay.setText(currentDay.dayNameInRussian);

        Stream.of(Place.SCHOOL).forEach(availablePlaces::add);
        colorActiveAndNotActiveButtons(Place.SCHOOL);

        currentPlace = Place.PLACE_FOR_SLEEP;
        lastUserCommandTime = System.currentTimeMillis();

        initScheduledExecutor();
    }


    @FXML
    void chillOutButtonIsClicked(ActionEvent event) {
        lastUserCommandTime = System.currentTimeMillis();
        executor.execute(() -> {
            lock.lock();
            chillOut();
            lock.lock();
        });
    }

    private void chillOut() {
        if (currentHour >= HOUR_TO_GO_TO_BED) {
            log.info(MSG_ABOUT_GO_TO_SLEEP);
            goToSleep();
            return;
        }
        if (!availablePlaces.contains(Place.HOUSE)) {
            Platform.runLater(() -> textFieldForInfo.setText("Не могу отдыхать"));
            return;
        }

        drawPersonInPlace(Place.HOUSE);

        currentHour += placesAndItsHours.get(Place.HOUSE);
        currentPlace = Place.HOUSE;

        availablePlaces.clear();
        Stream.of(Place.PLACE_FOR_SLEEP, Place.HOUSE, Place.WALKING_OUT_PLACE).forEach(availablePlaces::add);

        if (currentHour < HOUR_TO_GO_TO_BED) {
            colorActiveAndNotActiveButtons(Place.HOUSE, Place.WALKING_OUT_PLACE);
        } else {
            colorActiveAndNotActiveButtons(Place.PLACE_FOR_SLEEP);
        }

        if (!currentDay.dayIsWeekend) {
            availablePlaces.add(Place.PLACE_FOR_HOMEWORK);
        }

        updateTextFields(Place.HOUSE);
    }

    @FXML
    void doSubjectsButtonIsClicked(ActionEvent event) {
        lastUserCommandTime = System.currentTimeMillis();
        executor.execute(() -> {
            lock.lock();
            study();
            lock.unlock();
        });
    }

    private void study() {
        if (currentHour >= HOUR_TO_GO_TO_BED) {
            log.info(MSG_ABOUT_GO_TO_SLEEP);
            goToSleep();
            return;
        }
        if (!availablePlaces.contains(Place.PLACE_FOR_HOMEWORK)) {
            Platform.runLater(() -> textFieldForInfo.setText("Не могу делать уроки"));
            return;
        }

        drawPersonInPlace(Place.PLACE_FOR_HOMEWORK);

        currentHour += placesAndItsHours.get(Place.PLACE_FOR_HOMEWORK);
        currentPlace = Place.PLACE_FOR_HOMEWORK;

        availablePlaces.clear();
        Stream.of(Place.PLACE_FOR_SLEEP, Place.HOUSE, Place.WALKING_OUT_PLACE, Place.PLACE_FOR_HOMEWORK).forEach(availablePlaces::add);

        if (currentHour < HOUR_TO_GO_TO_BED) {
            colorActiveAndNotActiveButtons(Place.HOUSE, Place.WALKING_OUT_PLACE, Place.PLACE_FOR_HOMEWORK);
        } else {
            colorActiveAndNotActiveButtons(Place.PLACE_FOR_SLEEP);
        }

        updateTextFields(Place.PLACE_FOR_HOMEWORK);
    }

    @FXML
    void goToChessButtonIsClicked(ActionEvent event) {
        lastUserCommandTime = System.currentTimeMillis();
        executor.execute(() -> {
            lock.lock();
            doChess();
            lock.unlock();
        });
    }

    private void doChess() {
        if (currentHour >= HOUR_TO_GO_TO_BED) {
            log.info(MSG_ABOUT_GO_TO_SLEEP);
            goToSleep();
            return;
        }
        if (!availablePlaces.contains(Place.CHESS_HOUSE)) {
            Platform.runLater(() -> textFieldForInfo.setText("Не могу пойти на шахматы"));
            return;
        }

        drawPersonInPlace(Place.CHESS_HOUSE);

        currentHour += placesAndItsHours.get(Place.CHESS_HOUSE);
        currentPlace = Place.CHESS_HOUSE;

        availablePlaces.clear();
        Stream.of(Place.HOUSE, Place.PLACE_FOR_HOMEWORK, Place.WALKING_OUT_PLACE).forEach(availablePlaces::add);

        if (currentHour < HOUR_TO_GO_TO_BED) {
            colorActiveAndNotActiveButtons(Place.HOUSE, Place.PLACE_FOR_HOMEWORK, Place.WALKING_OUT_PLACE);
        } else {
            colorActiveAndNotActiveButtons(Place.PLACE_FOR_SLEEP);
        }

        updateTextFields(Place.CHESS_HOUSE);
    }

    @FXML
    void goToSchoolButtonIsClicked(ActionEvent event) {
        lastUserCommandTime = System.currentTimeMillis();
        executor.execute(() -> {
            lock.lock();
            goToSchool();
            lock.unlock();
        });
    }

    private void goToSchool() {
        if (currentHour >= HOUR_TO_GO_TO_BED) {
            log.info(MSG_ABOUT_GO_TO_SLEEP);
            goToSleep();
            return;
        }
        if (!availablePlaces.contains(Place.SCHOOL)) {
            Platform.runLater(() -> textFieldForInfo.setText("Не могу пойти в школу"));
            return;
        }

        drawPersonInPlace(Place.SCHOOL);

        currentHour += placesAndItsHours.get(Place.SCHOOL);
        currentPlace = Place.SCHOOL;

        availablePlaces.clear();
        Stream.of(Place.CHESS_HOUSE, Place.HOUSE, Place.PLACE_FOR_HOMEWORK, Place.WALKING_OUT_PLACE).forEach(availablePlaces::add);

        if (currentHour < HOUR_TO_GO_TO_BED) {
            colorActiveAndNotActiveButtons(Place.CHESS_HOUSE, Place.HOUSE, Place.PLACE_FOR_HOMEWORK, Place.WALKING_OUT_PLACE);
        } else {
            colorActiveAndNotActiveButtons(Place.PLACE_FOR_SLEEP);
        }

        updateTextFields(Place.SCHOOL);
    }

    @FXML
    void sleepButtonIsClicked(ActionEvent event) {
        lastUserCommandTime = System.currentTimeMillis();
        executor.execute(() -> {
            lock.lock();
            goToSleep();
            lock.unlock();
        });
    }

    private void goToSleep() {
        if (currentHour < HOUR_TO_GO_TO_BED) {
            Platform.runLater(() -> textFieldForInfo.setText(MSG_ABOUT_NOT_GO_TO_SLEEP));
            return;
        }
        if (!availablePlaces.contains(Place.PLACE_FOR_SLEEP)) {
            Platform.runLater(() -> textFieldForInfo.setText("Не могу пойти спать"));
            return;
        }

        drawPersonInPlace(Place.PLACE_FOR_SLEEP);

        currentDay = currentDay.nextDay();
        currentHour = TIME_TO_WAKE_UP;
        currentPlace = Place.PLACE_FOR_SLEEP;

        availablePlaces.clear();
        placeAndItsButton.values().forEach(button -> {
            button.getStyleClass().remove(STYLE_FOR_AVAILABLE_BUTTON);
            button.getStyleClass().add(STYLE_FOR_UNAVAILABLE_BUTTON);
        });

        boolean hasBadMood = ThreadLocalRandom.current().nextBoolean();
        if (currentDay.dayIsWeekend || hasBadMood) {
            Stream.of(Place.HOUSE, Place.WALKING_OUT_PLACE).forEach(availablePlaces::add);
            colorActiveButtons(Place.HOUSE, Place.WALKING_OUT_PLACE);
        }
        if (!currentDay.dayIsWeekend) {
            Stream.of(Place.SCHOOL).forEach(availablePlaces::add);
            colorActiveButtons(Place.SCHOOL);
        }

        Platform.runLater(() -> {
            if (hasBadMood && !currentDay.dayIsWeekend) {
                textFieldForInfo.setText("Я поспал и не хочу идти в школу");
            } else {
                textFieldForInfo.setText(Place.PLACE_FOR_SLEEP.placeInRussian);
            }
            textFieldForHour.setText(currentHour + ":00");
            textFieldForDay.setText(currentDay.dayNameInRussian);
        });
    }

    @FXML
    void walkingOutButtonIsClicked(ActionEvent event) {
        lastUserCommandTime = System.currentTimeMillis();
        executor.execute(() -> {
            lock.lock();
            walkUp();
            lock.unlock();
        });
    }

    private void walkUp() {
        if (currentHour >= HOUR_TO_GO_TO_BED) {
            log.info(MSG_ABOUT_GO_TO_SLEEP);
            goToSleep();
            return;
        }
        if (!availablePlaces.contains(Place.WALKING_OUT_PLACE)) {
            Platform.runLater(() -> textFieldForInfo.setText("Не могу гулять"));
            return;
        }

        drawPersonInPlace(Place.WALKING_OUT_PLACE);

        currentHour += placesAndItsHours.get(Place.WALKING_OUT_PLACE);
        currentPlace = Place.WALKING_OUT_PLACE;

        availablePlaces.clear();
        Stream.of(Place.PLACE_FOR_SLEEP, Place.HOUSE, Place.WALKING_OUT_PLACE).forEach(availablePlaces::add);

        if (currentHour < HOUR_TO_GO_TO_BED) {
            colorActiveAndNotActiveButtons(Place.HOUSE, Place.WALKING_OUT_PLACE);
        } else {
            colorActiveAndNotActiveButtons(Place.PLACE_FOR_SLEEP);
        }

        if (!currentDay.dayIsWeekend) {
            availablePlaces.add(Place.PLACE_FOR_HOMEWORK);
            colorActiveButtons(Place.PLACE_FOR_HOMEWORK);
        }

        updateTextFields(Place.WALKING_OUT_PLACE);
    }

    private void drawPersonInPlace(Place place) {
        Platform.runLater(() -> {
            Point housePoint = placesAndItsMapPoints.get(place);
            personImageView.setX(housePoint.getX());
            personImageView.setY(housePoint.getY());
        });
    }

    private void updateTextFields(Place place) {
        Platform.runLater(() -> {
            textFieldForInfo.setText(place.placeInRussian);
            textFieldForHour.setText(currentHour + ":00");
            textFieldForDay.setText(currentDay.dayNameInRussian);
        });
    }

    private void initScheduledExecutor() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            lock.lock();
            if (System.currentTimeMillis() - lastUserCommandTime < TIME_TO_CHECK_UPDATE || currentPlace == Place.PLACE_FOR_SLEEP) {
                log.info("Хочу сделать ход за пользователя, но не могу");
                lock.unlock();
                return;
            }

            log.info("Делаю ход за пользователя");

            currentHour += placesAndItsHours.get(currentPlace);
            if (currentHour >= HOUR_TO_GO_TO_BED) {
                availablePlaces.add(Place.PLACE_FOR_SLEEP);
                goToSleep();
            } else {
                updateTextFields(currentPlace);
            }
            lock.unlock();
        }, TIME_TO_CHECK_UPDATE, TIME_TO_CHECK_UPDATE, TimeUnit.MILLISECONDS);
    }

    private void colorActiveAndNotActiveButtons(Place... availablePlaces) {
        Platform.runLater(() -> {
            placeAndItsButton
                    .values()
                    .forEach(button -> {
                        button.getStyleClass().remove(STYLE_FOR_AVAILABLE_BUTTON);
                        button.getStyleClass().add(STYLE_FOR_UNAVAILABLE_BUTTON);
                    });

            Arrays
                    .stream(availablePlaces)
                    .map(placeAndItsButton::get)
                    .forEach(button -> {
                        button.getStyleClass().remove(STYLE_FOR_UNAVAILABLE_BUTTON);
                        button.getStyleClass().add(STYLE_FOR_AVAILABLE_BUTTON);
                    });
        });
    }

    private void colorActiveButtons(Place... availablePlaces) {
        Platform.runLater(() -> Arrays
                .stream(availablePlaces)
                .map(placeAndItsButton::get)
                .forEach(button -> {
                    button.getStyleClass().remove(STYLE_FOR_UNAVAILABLE_BUTTON);
                    button.getStyleClass().add(STYLE_FOR_AVAILABLE_BUTTON);
                }));
    }
}
