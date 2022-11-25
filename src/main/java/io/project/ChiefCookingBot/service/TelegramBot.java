package io.project.ChiefCookingBot.service;

import com.vdurmont.emoji.EmojiParser;
import io.project.ChiefCookingBot.config.BotConfig;
import io.project.ChiefCookingBot.model.AdsRepository;
import io.project.ChiefCookingBot.model.User;
import io.project.ChiefCookingBot.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.*;

@Component
@Slf4j


public class TelegramBot extends TelegramLongPollingBot {


    @Autowired
    private AdsRepository adsRepository;
    @Autowired
    private UserRepository userRepository;

    final BotConfig config;


    static final String HELP_TEXT = "Этот бот создан для поиска рецептов и помощи в приготовлении блюд.\n\nСуществует ряд команд, которые бот может выполнять, такие как: \n" +
            "/start" + " для отображения приветственного сообщения \n" +
    //      Add the necessary text with hints/commands, for example:
    //      "/mydata" + " для отображения информации о пользователе \n" +
    //      "/deletedata" + " для удаления информации о пользователе \n" +
    //      "/settings" + " для изменения настроек пользователя.\n\n" +
            "\nА также Кулинарный Шеф может осуществлять поиск рецептов по ингредиентам. Попробуйте написать мне, что у Вас есть (через пробел), а я пришлю блюда, которые возможно из данного набора приготовить!";

    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";

    static final String ERROR_TEXT = "Error occurred: ";


    // menu button commands
    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add (new BotCommand("/start", "Приветственное сообщение"));
        //  listOfCommands.add (new BotCommand("/mydata", "Информация о пользователе"));
        //  listOfCommands.add (new BotCommand("/deletedata", "Удалить информацию о пользователе"));
        listOfCommands.add (new BotCommand("/help", "Информация, что умеет Кулинарный Шеф"));
        //  listOfCommands.add (new BotCommand("/settings", "Изменение настроек"));
        listOfCommands.add (new BotCommand("/register", "Пробная кнопка"));
        try{
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        }
        catch (TelegramApiException e){
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {


        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            // added the ability to display emoji and manually send a message to all registered users
            if (messageText.contains("/sendToAll") && config.getOwnerId() == chatId) {
                var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                var users = userRepository.findAll();
                for (User user: users){
                    prepareAndSandMessage(user.getChatId(), textToSend);
                }
            }
            else {

                switch (messageText) {
                    case "/start":
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "/help":
                        prepareAndSandMessage(chatId, HELP_TEXT);
                        break;

                    case "/register":
                        register(chatId);
                        break;

                    case "/sendToAll":
                        break;

                    case "Русская кухня":
                        prepareAndSandMessage(chatId,"https://eda.ru/recepty/russkaya-kuhnya");
                        break;

                    case "Итальянская кухня":
                        prepareAndSandMessage(chatId, "https://eda.ru/recepty/italyanskaya-kuhnya");
                        break;

                    case "Французская кухня":
                        prepareAndSandMessage(chatId, "https://eda.ru/recepty/francuzskaya-kuhnya");
                        break;

                    case "Японская кухня":
                        prepareAndSandMessage(chatId, "https://eda.ru/recepty/yaponskaya-kuhnya");
                        break;

                    case "Мексиканская кухня":
                        prepareAndSandMessage(chatId, "https://eda.ru/recepty/meksikanskaya-kuhnya");
                        break;

                    case "Другие блюда":
                        prepareAndSandMessage(chatId, "https://eda.ru/recepty");
                        break;


                    default:
                        String messageTextWithPlus;
                        messageTextWithPlus = messageText.replace(' ', '+');
                        prepareAndSandMessage(chatId, "https://eda.ru/recipesearch?q=" + messageTextWithPlus + "&onlyEdaChecked=false");
                }
            }
        //popup button testing
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if(callbackData.equals(YES_BUTTON)){
            String text = "You pressed YES button";
                executeEditMessageText(text,chatId,messageId);
            }
            else if (callbackData.equals(NO_BUTTON)) {
            String text = "You pressed NO button";
                executeEditMessageText(text,chatId,messageId);
            }
        }

    }

    //popup button testing
    private void register(long chatId) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText("Do you really want to register?");
        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();

        var yesButton = new InlineKeyboardButton();
        yesButton.setText("Yes");
        yesButton.setCallbackData(YES_BUTTON);

        var noButton = new InlineKeyboardButton();
        noButton.setText("No");
        noButton.setCallbackData(NO_BUTTON);

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);


    }
    //recording data about registered users
    private void registerUser(Message msg) {
        if(userRepository.findById(msg.getChatId()).isEmpty()){
            var chatId = msg.getChatId();
            var chat = msg.getChat();
            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("user saved " + user);

        }
    }

    private void startCommandReceived(long chatId, String name)  {
        String answer = EmojiParser.parseToUnicode("Привет, " + name + " , приятно познакомиться! Я кулинарный бот. Пока еще не все умею, но скоро научусь. Я смогу помогать тебе с поиском рецептов, исходя из наличия ингредиентов у тебя на кухне" + ":avocado:" + ":tomato:" + ":carrot:" + ", или просто рецептов блюд, которые тебя интересуют" + ":stew:");
        log.info("Replied to user: " + name);
        sendMessage(chatId, answer);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        //popup menu under the keyboard
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Русская кухня");
        row.add("Итальянская кухня");
        row.add("Французская кухня");

        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("Японская кухня");
        row.add("Мексиканская кухня");
        row.add("Другие блюда");

        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    private void executeEditMessageText(String text, long chatId, long messageId){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int)messageId);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }
    private void executeMessage(SendMessage message){
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void prepareAndSandMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }



    //the ability to send pre-created messages in a specified period of time

   /* @Scheduled(cron = "${cron.scheduler}")
    private void sendAds() {
        var ads = adsRepository.findAll();
        var users = userRepository.findAll();
        for (Ads ad: ads) {
            for (User user: users) {
                prepareAndSandMessage(user.getChatId(), ad.getAd());
            }
        }
    }
    */

}