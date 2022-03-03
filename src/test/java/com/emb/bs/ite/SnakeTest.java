package com.emb.bs.ite;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.Session;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SnakeTest {

    private static final Logger LOG = LoggerFactory.getLogger(SnakeTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    }

    private Snake.Handler handler;

    @BeforeEach
    void setUp() {
        handler = new Snake.Handler();
    }

    @Test
    void replay() throws Exception{
        String[] currentDir = new File(new File("").getAbsolutePath()).list();
        if(currentDir != null && currentDir.length >0) {
            Arrays.sort(currentDir, Collections.reverseOrder());
            for (String fName : currentDir) {
                if (fName.startsWith("req_")) {
                    List<String> simJson = readFileLineByLineAsList((new File(fName).toPath()));
                    LOG.info("REPLAY: "+fName);
                    handler.start(OBJECT_MAPPER.readTree(simJson.remove(0)));
                    for (String line : simJson) {
                        handler.move(OBJECT_MAPPER.readTree(line));
                    }
                    break;
                }
            }
        }
    }

    @ClientEndpoint
    public class SimpleWSSHandler implements OnOpen, OnClose, OnMessage{
        ArrayNode list = OBJECT_MAPPER.createArrayNode();
        Session session = null;

        @OnOpen
        public void onOpen(Session userSession) {
            this.session = userSession;
        }

        @OnClose
        public void onClose(Session aSession, CloseReason reason) {
            this.session = null;
        }

        @OnMessage
        public void onMessage(String message) {
            try {
                JsonNode json = OBJECT_MAPPER.readTree(message.toLowerCase().replaceAll("-1", "0"));
                JsonNode content = json.get("data");
                if(content.has("turn")) {
                    list.add(content);
                }
            } catch (JsonProcessingException e) {}
        }

        @OnMessage
        public void onMessage(ByteBuffer bytes) {}

        @Override
        public long maxMessageSize() {
            return Integer.MAX_VALUE;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return null;
        }
    }

    @Test
    void replayGameWithId() throws Exception{

        // THIS is a GAME where @ move 81 I should try to get away from other snake heads
        // https://play.battlesnake.com/g/8d9706c0-71cd-49a2-a823-d1a81bd2be67/
        //String gameId= "8d9706c0-71cd-49a2-a823-d1a81bd2be67";

        // here from move 35 we have to make better decisions... MOVE away from
        // other snake heads + MOVE AWAY from Borders!
        // d6f67061-26e2-4f79-be24-e18ab6141916

        // here we move up at 621 (to get food) - but this will end in a DOOMED state
        // IMPLEMENT - FOOD/KILL moves have to also consider alternative moves [and
        // skip if they will be fatal]
        // ebc6ac78-21fa-4955-8b95-60b4ad3d684a

        // one other snake - decide to move UP and not LEFT (because we will move to danger)
        // "23061a1d-8c79-4417-9c72-c4db6724ccfd";
        // turn 121 / 11x11

        String gameId = "fc74ede6-20f7-4c0f-88ac-22b7316b6634";
        int turn = 171; // ??

        String yourNameIdentifier = "lender";
        String gameMode = null;
        //String gameMode = "wrapped";
        //String gameMode = "royale";
        int Y = 11;
        int X = 11;

        SimpleWSSHandler collector = new SimpleWSSHandler();
        URI uri = new URI("wss://engine.battlesnake.com/games/"+gameId+"/events");
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(collector, uri);

        // wait 5 seconds for messages from websocket
        Thread.sleep(5000);

        handler.start(convertToReq(collector.list.get(0), gameId, gameMode, Y, X, yourNameIdentifier));
        for(int i=0; i < collector.list.size()-1 ; i++){
            JsonNode req = convertToReq(collector.list.get(i), gameId, gameMode, Y, X, yourNameIdentifier);
            if(i == turn){
                LOG.info("DEBUG NOW");
            }
            handler.move(req);
        }
        handler.end(convertToReq(collector.list.get(collector.list.size()-1), gameId, gameMode, Y, X, yourNameIdentifier));
    }

    private JsonNode convertToReq(JsonNode replayJson, String gameId, String gameMode, int Y, int X, String selfIdentifier) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode game = OBJECT_MAPPER.createObjectNode();
        root.put("game", game);
        game.put("id", gameId);
        if(gameMode != null){
            ObjectNode ruleset = OBJECT_MAPPER.createObjectNode();
            game.put("ruleset", ruleset);
            ruleset.put("name", gameMode);
        }

        root.put("turn", replayJson.get("turn").asInt());

        ObjectNode board = OBJECT_MAPPER.createObjectNode();
        root.put("board", board);

        // WARING HARDCODE BOARD SIZE!
        board.put("height", Y);
        board.put("width", X);

        board.put("food", replayJson.get("food"));
        board.put("hazards", replayJson.get("hazards"));

        ArrayNode targetSnakes = OBJECT_MAPPER.createArrayNode();
        board.put("snakes", targetSnakes);

        JsonNode srcSnakes = replayJson.get("snakes");
        int sLen = srcSnakes.size();

        for(int i=0; i<sLen; i++){
            JsonNode srcS = srcSnakes.get(i);
            boolean isYou = srcS.get("name").asText().indexOf(selfIdentifier)>-1;
            if(srcS.get("death").isNull() || isYou){
                ObjectNode dest = OBJECT_MAPPER.createObjectNode();
                if(isYou){
                    ObjectNode you = OBJECT_MAPPER.createObjectNode();
                    root.put("you", you);
                    dest = you;
                }else{
                    targetSnakes.add(dest);
                }
                dest.put("id", srcS.get("id").asText());
                dest.put("name", srcS.get("name").asText());
                dest.put("body", srcS.get("body"));
                dest.put("head", srcS.get("body").get(0));
                dest.put("length", srcS.get("body").size());
                dest.put("health", srcS.get("health").intValue());
            }
        }
        return root;
    }

    /*
    @Test
    void indexTest() throws IOException {
        Map<String, String> response = handler.index();
        assertEquals("#888888", response.get("color"));
        assertEquals("default", response.get("head"));
        assertEquals("default", response.get("tail"));
    }

    @Test
    void startTest() throws IOException {
        JsonNode startRequest = OBJECT_MAPPER.readTree("{}");
        Map<String, String> response = handler.end(startRequest);
        assertEquals(0, response.size());

    }

    @Test
    void moveTest() throws IOException {
        JsonNode moveRequest = OBJECT_MAPPER.readTree(
                "{\"game\":{\"id\":\"game-00fe20da-94ad-11ea-bb37\",\"ruleset\":{\"name\":\"standard\",\"version\":\"v.1.2.3\"},\"timeout\":500},\"turn\":14,\"board\":{\"height\":11,\"width\":11,\"food\":[{\"x\":5,\"y\":5},{\"x\":9,\"y\":0},{\"x\":2,\"y\":6}],\"hazards\":[{\"x\":3,\"y\":2}],\"snakes\":[{\"id\":\"snake-508e96ac-94ad-11ea-bb37\",\"name\":\"My Snake\",\"health\":54,\"body\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":0},{\"x\":2,\"y\":0}],\"latency\":\"111\",\"head\":{\"x\":0,\"y\":0},\"length\":3,\"shout\":\"why are we shouting??\",\"squad\":\"\"},{\"id\":\"snake-b67f4906-94ae-11ea-bb37\",\"name\":\"Another Snake\",\"health\":16,\"body\":[{\"x\":5,\"y\":4},{\"x\":5,\"y\":3},{\"x\":6,\"y\":3},{\"x\":6,\"y\":2}],\"latency\":\"222\",\"head\":{\"x\":5,\"y\":4},\"length\":4,\"shout\":\"I'm not really sure...\",\"squad\":\"\"}]},\"you\":{\"id\":\"snake-508e96ac-94ad-11ea-bb37\",\"name\":\"My Snake\",\"health\":54,\"body\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":0},{\"x\":2,\"y\":0}],\"latency\":\"111\",\"head\":{\"x\":0,\"y\":0},\"length\":3,\"shout\":\"why are we shouting??\",\"squad\":\"\"}}");
        Map<String, String> response = handler.move(moveRequest);

        List<String> options = new ArrayList<String>();
        options.add("up");
        options.add("down");
        options.add("left");
        options.add("right");

        assertTrue(options.contains(response.get("move")));
    }

    @Test
    void endTest() throws IOException {
        JsonNode endRequest = OBJECT_MAPPER.readTree("{}");
        Map<String, String> response = handler.end(endRequest);
        assertEquals(0, response.size());
    }

    @Test
    void avoidNeckAllTest() throws IOException {

        JsonNode testHead = OBJECT_MAPPER.readTree("{\"x\": 5, \"y\": 5}");
        JsonNode testBody = OBJECT_MAPPER
                .readTree("[{\"x\": 5, \"y\": 5}, {\"x\": 5, \"y\": 5}, {\"x\": 5, \"y\": 5}]");
        ArrayList<String> possibleMoves = new ArrayList<>(Arrays.asList("up", "down", "left", "right"));
        ArrayList<String> expectedResult = new ArrayList<>(Arrays.asList("up", "down", "left", "right"));

        //handler.avoidMyNeck(testHead, testBody, possibleMoves);

        assertTrue(possibleMoves.size() == 4);
        assertTrue(possibleMoves.equals(expectedResult));
    }

    @Test
    void avoidNeckLeftTest() throws IOException {

        JsonNode testHead = OBJECT_MAPPER.readTree("{\"x\": 5, \"y\": 5}");
        JsonNode testBody = OBJECT_MAPPER
                .readTree("[{\"x\": 5, \"y\": 5}, {\"x\": 4, \"y\": 5}, {\"x\": 3, \"y\": 5}]");
        ArrayList<String> possibleMoves = new ArrayList<>(Arrays.asList("up", "down", "left", "right"));
        ArrayList<String> expectedResult = new ArrayList<>(Arrays.asList("up", "down", "right"));

        //handler.avoidMyNeck(testHead, testBody, possibleMoves);

        assertTrue(possibleMoves.size() == 3);
        assertTrue(possibleMoves.equals(expectedResult));
    }

    @Test
    void avoidNeckRightTest() throws IOException {

        JsonNode testHead = OBJECT_MAPPER.readTree("{\"x\": 5, \"y\": 5}");
        JsonNode testBody = OBJECT_MAPPER
                .readTree("[{\"x\": 5, \"y\": 5}, {\"x\": 6, \"y\": 5}, {\"x\": 7, \"y\": 5}]");
        ArrayList<String> possibleMoves = new ArrayList<>(Arrays.asList("up", "down", "left", "right"));
        ArrayList<String> expectedResult = new ArrayList<>(Arrays.asList("up", "down", "left"));

        //handler.avoidMyNeck(testHead, testBody, possibleMoves);

        assertTrue(possibleMoves.size() == 3);
        assertTrue(possibleMoves.equals(expectedResult));
    }

    @Test
    void avoidNeckUpTest() throws IOException {

        JsonNode testHead = OBJECT_MAPPER.readTree("{\"x\": 5, \"y\": 5}");
        JsonNode testBody = OBJECT_MAPPER
                .readTree("[{\"x\": 5, \"y\": 5}, {\"x\": 5, \"y\": 6}, {\"x\": 5, \"y\": 7}]");
        ArrayList<String> possibleMoves = new ArrayList<>(Arrays.asList("up", "down", "left", "right"));
        ArrayList<String> expectedResult = new ArrayList<>(Arrays.asList("down", "left", "right"));

        //handler.avoidMyNeck(testHead, testBody, possibleMoves);

        assertTrue(possibleMoves.size() == 3);
        assertTrue(possibleMoves.equals(expectedResult));
    }

    @Test
    void avoidNeckDownTest() throws IOException {

        JsonNode testHead = OBJECT_MAPPER.readTree("{\"x\": 5, \"y\": 5}");
        JsonNode testBody = OBJECT_MAPPER
                .readTree("[{\"x\": 5, \"y\": 5}, {\"x\": 5, \"y\": 4}, {\"x\": 5, \"y\": 3}]");
        ArrayList<String> possibleMoves = new ArrayList<>(Arrays.asList("up", "down", "left", "right"));
        ArrayList<String> expectedResult = new ArrayList<>(Arrays.asList("up", "left", "right"));

        //handler.avoidMyNeck(testHead, testBody, possibleMoves);

        assertTrue(possibleMoves.size() == 3);
        assertTrue(possibleMoves.equals(expectedResult));
    }
    */

    private static String[] readFileLineByLine(Path inputFilePath) {
        Charset charset = Charset.defaultCharset();
        List<String> stringList = null;
        try {
            stringList = Files.readAllLines(inputFilePath, charset);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringList.toArray(new String[]{});
    }

    private static List<String> readFileLineByLineAsList(Path inputFilePath) {
        Charset charset = Charset.defaultCharset();
        List<String> stringList = null;
        try {
            stringList = Files.readAllLines(inputFilePath, charset);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringList;
    }

    private static String readFileAsString(Path inputFilePath) {
        Charset charset = Charset.defaultCharset();
        String data = null;
        try {
            byte[] bytes = Files.readAllBytes(inputFilePath);
            data = new String(bytes, charset);
            //data = Files.readString(inputFilePath, charset);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }
}