package com.emb.bs.ite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.*;

import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.get;

/**
 * This is a simple Battlesnake server written in Java.
 * 
 * For instructions see
 * https://github.com/BattlesnakeOfficial/starter-snake-java/README.md
 */
public class Snake {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Handler HANDLER = new Handler();
    private static final Logger LOG = LoggerFactory.getLogger(Snake.class);

    static final String U = "up";
    static final String D = "down";
    static final String L = "left";
    static final String R = "right";

    static boolean logBoard = false;
    static int debugTurn = -1;
    /**
     * Main entry point.
     *
     * @param args are ignored.
     */
    public static void main(String[] args) {
        String port = System.getProperty("PORT");
        if (port == null) {
            LOG.info("Using default port: {}", port);
            port = "9191";
            logBoard = true;
        } else {
            LOG.info("Found system provided port: {}", port);
        }
        port(Integer.parseInt(port));
        get("/", HANDLER::process, JSON_MAPPER::writeValueAsString);
        post("/start", HANDLER::process, JSON_MAPPER::writeValueAsString);
        post("/move", HANDLER::process, JSON_MAPPER::writeValueAsString);
        post("/end", HANDLER::process, JSON_MAPPER::writeValueAsString);
    }

    /**
     * Handler class for dealing with the routes set up in the main method.
     */
    public static class Handler {

        /**
         * For the start/end request
         */
        private static final Map<String, String> EMPTY = new HashMap<>();
        private HashMap<String, Session> sessions = new HashMap();

        /**
         * Generic processor that prints out the request and response from the methods.
         *
         * @param req
         * @param res
         * @return
         */
        public Map<String, String> process(Request req, Response res) {
            try {
                JsonNode parsedRequest = JSON_MAPPER.readTree(req.body());
                String uri = req.uri();
                Map<String, String> snakeResponse;
                if (uri.equals("/")) {
                    snakeResponse = index();
                } else if (uri.equals("/start")) {
                    snakeResponse = start(parsedRequest);
                } else if (uri.equals("/move")) {
                    snakeResponse = move(parsedRequest);
                } else if (uri.equals("/end")) {
                    //LOG.info("{} called with: {}", uri, req.body());
                    snakeResponse = end(parsedRequest);
                } else {
                    throw new IllegalAccessError("Strange call made to the snake: " + uri);
                }
                //LOG.info("Responding with: {}", JSON_MAPPER.writeValueAsString(snakeResponse));
                return snakeResponse;
            } catch (JsonProcessingException e) {
                LOG.warn("Something went wrong!", e);
                return null;
            }
        }

        /**
         * This method is called everytime your Battlesnake is entered into a game.
         * 
         * Use this method to decide how your Battlesnake is going to look on the board.
         *
         * @return a response back to the engine containing the Battlesnake setup
         *         values.
         */
        public Map<String, String> index() {
            Map<String, String> response = new HashMap<>();
            response.put("apiversion", "1");
            response.put("author", "marq24");
            // release
            //response.put("color", "#33cc33");
            // beta

            // pastel green (nina)
            //response.put("color", "#3CFBA1");

            // my beta YELLOW
            response.put("color", "#cccc33");

            //response.put("color", "#3333cc");
            //response.put("color", "#FF1111");
            // https://play.battlesnake.com/references/customizations/
            response.put("head", "sand-worm"); // TODO: Personalize
            response.put("tail", "block-bum"); // TODO: Personalize
            return response;
        }

        /**
         * This method is called everytime your Battlesnake is entered into a game.
         * 
         * Use this method to decide how your Battlesnake is going to look on the board.
         *
         * @param startRequest a JSON data map containing the information about the game
         *                     that is about to be played.
         * @return responses back to the engine are ignored.
         */
        public Map<String, String> start(JsonNode startRequest) {
            LOG.info("START");
            Session s = new Session();
            sessions.put(startRequest.get("game").get("id").asText(), s);

            s.players = new ArrayList<>();
            JsonNode snakes = startRequest.get("board").get("snakes");
            int sLen = snakes.size();
            for (int i = 0; i < sLen; i++) {
                s.players.add(snakes.get(i).get("name").asText());
            }
            return EMPTY;
        }

        /**
         * This method is called on every turn of a game. It's how your snake decides
         * where to move.
         * 
         * Use the information in 'moveRequest' to decide your next move. The
         * 'moveRequest' variable can be interacted with as
         * com.fasterxml.jackson.databind.JsonNode, and contains all of the information
         * about the Battlesnake board for each move of the game.
         * 
         * For a full example of 'json', see
         * https://docs.battlesnake.com/references/api/sample-move-request
         *
         * @param moveRequest JsonNode of all Game Board data as received from the
         *                    Battlesnake Engine.
         * @return a Map<String,String> response back to the engine the single move to
         *         make. One of "up", "down", "left" or "right".
         */

        public Map<String, String> move(JsonNode moveRequest) {
            JsonNode game = moveRequest.get("game");
            String sessId = game.get("id").asText();
            Session s = sessions.get(sessId);

            if(sessions.size() == 0){
                // ok the server was just restarted during a game session - not so good - but everything is better
                // than instant death...
                s = new Session();
                s.players = new ArrayList<>();
                sessions.put(sessId, s);
                LOG.warn(sessId+" WAS NOT FOUND - probably cause of SERVER-RESTART?! - JUST continue with default");
            }

            if(s != null) {
                s.gameId = sessId;
                String gameType = null;
                if(game.has("ruleset")){
                    gameType = game.get("ruleset").get("name").asText().toLowerCase();
                }
                readCurrentBoardStatusIntoSession(moveRequest, gameType, s);

                MoveWithState moveWithState = s.calculateNextMoveOptions();
                String move = s.getMoveIntAsString(moveWithState.move);

                if(moveWithState.move == Session.DOOMED){
                    // OK we are DOOMED anyhow - so we can do what ever
                    // we want -> so we just repeat the last move...
                    move = s.LASTMOVE;
                    if(move == null){
                        // WTF?!
                        move = D;
                    }
                }else{
                    s.LASTMOVE = move;
                }

                // when we will eat food with our next move, then out tail will stay
                // where he currently is... in any other case we will update
                // the 'lastTurnTail'
                Point resultPos = s.getNewPointForDirection(s.myHead, moveWithState.move);
                if(!s.foodPlaces.contains(resultPos)){
                    s.lastTurnTail = s.myTail;
                }

                LOG.info("=> RESULTING MOVE: "+moveWithState);
                Map<String, String> response = new HashMap<>();
                response.put("move", move);
                return response;
            } else {
                // session is null ?!
                LOG.error("SESSION was not available?! -> "+sessId+" could not be found in: "+sessions);
                Map<String, String> response = new HashMap<>();
                response.put("move", R);
                return response;
            }
        }

        private void readCurrentBoardStatusIntoSession(JsonNode moveRequest, String rulesetName, Session s) {
            s.turn = moveRequest.get("turn").asInt();
            JsonNode board = moveRequest.get("board");

            // get OWN SnakeID
            JsonNode you = moveRequest.get("you");
            String myId = you.get("id").asText();

            s.myHealth = you.get("health").asInt();
            s.myLen = you.get("length").asInt();

            // clearing the used session fields...
            s.initSessionForTurn(rulesetName, board.get("height").asInt(), board.get("width").asInt());

            JsonNode head = you.get("head");
            s.myHead = new Point(head);
            // adding also myHead to the body array (to allow
            // simple NoGoZone-Detection
            s.myBody[s.myHead.y][s.myHead.x] = s.myLen;

            JsonNode myBody = you.get("body");
            int myBodyLen = myBody.size()-1;
            s.myTail = new Point(myBody.get(myBodyLen-1));
            for (int i = 1; i < myBodyLen; i++) {
                Point p = new Point(myBody.get(i));
                s.myBody[p.y][p.x] = 1;
            }

            // reading about available food...
            JsonNode food = board.get("food");
            if (food != null) {
                int fLen = food.size();
                for (int i = 0; i < fLen; i++) {
                    s.foodPlaces.add(new Point(food.get(i)));
                }
            }

            // get the locations of all snakes...
            JsonNode snakes = board.get("snakes");
            int sLen = snakes.size();
            for (int i = 0; i < sLen; i++) {
                JsonNode aSnake = snakes.get(i);
                if (!aSnake.get("id").asText().equals(myId)) {

                    int snakeLen = aSnake.get("length").asInt();
                    s.maxOtherSnakeLen = Math.max(snakeLen, s.maxOtherSnakeLen);
                    Point h = new Point(aSnake.get("head"));
                    s.snakeBodies[h.y][h.x] = snakeLen;
                    s.snakeHeads.add(h);

                    // dealing with the bodies of the other snakes...
                    JsonNode body = aSnake.get("body");
                    int bLen = body.size();

                    // a) we start from j=1 here - since we have handled the SneakHEAD's already
                    // b) we also do not have top care about the LAST entry in the body, since this
                    // we be always FREE after "this" turn (if the snake grows, that the last
                    // and the prev record of the body contain the same position!)
                    for (int j = 1; j < bLen-1; j++) {
                        Point p = new Point(body.get(j));
                        s.snakeBodies[p.y][p.x] = 1;
                    }

                    int newYDown = -1;
                    int newYUp = -1;
                    int newXLeft = -1;
                    int newXRight = -1;
                    if(s.mWrappedMode){
                        newYDown = (h.y - 1 + s.Y) % s.Y;
                        newYUp = (h.y + 1) % s.Y;
                        newXLeft = (h.x - 1 + s.X) % s.X;
                        newXRight = (h.x + 1) % s.X;
                    }else{
                        if(h.y > 0){
                            newYDown = h.y - 1;
                        }
                        if(h.y < s.Y - 1){
                            newYUp = h.y + 1;
                        }
                        if(h.x > 0){
                            newXLeft = h.x - 1;
                        }
                        if(h.x < s.X - 1 ){
                            newXRight = h.x + 1;
                        }
                    }

                    if (newYDown > -1 && s.snakeBodies[newYDown][h.x] == 0) {
                        handleSnakeNextMovePos(snakeLen, new Point(newYDown, h.x), s);
                        s.snakeNextMovePossibleLocations[newYDown][h.x] = Math.max(snakeLen, s.snakeNextMovePossibleLocations[newYDown][h.x]);
                    }
                    if (newYUp > -1 && s.snakeBodies[newYUp][h.x] == 0) {
                        handleSnakeNextMovePos(snakeLen, new Point(newYUp, h.x), s);
                        s.snakeNextMovePossibleLocations[newYUp][h.x] = Math.max(snakeLen, s.snakeNextMovePossibleLocations[newYUp][h.x]);
                    }
                    if (newXLeft > -1 && s.snakeBodies[h.y][newXLeft] == 0) {
                        handleSnakeNextMovePos(snakeLen, new Point(h.y, newXLeft), s);
                        s.snakeNextMovePossibleLocations[h.y][newXLeft] = Math.max(snakeLen, s.snakeNextMovePossibleLocations[h.y][newXLeft]);
                    }
                    if (newXRight > -1 && s.snakeBodies[h.y][newXRight] == 0) {
                        handleSnakeNextMovePos(snakeLen, new Point(h.y, newXRight), s);
                        s.snakeNextMovePossibleLocations[h.y][newXRight] = Math.max(snakeLen, s.snakeNextMovePossibleLocations[h.y][newXRight]);
                    }
                }
            }

            JsonNode haz = board.get("hazards");
            if (haz != null) {
                int hLen = haz.size();
                if(hLen > 0) {
                    for (int i = 0; i < hLen; i++) {
                        Point h = new Point(haz.get(i));
                        s.hazardZone[h.y][h.x] = 1;
                    }
                }else{
                    haz = null;
                }
            }

            // after we have read all positions/Objects we might to additionally init the current
            // session status...
            s.initSessionAfterFullBoardRead(haz != null);
            LOG.info("MOVE CALLED - Turn: "+s.turn+" ["+s.gameId+"]");
            //s.logState("MOVE CALLED", LOG);
            if(logBoard) {
                s.logBoard(LOG);
            }
        }

        private void handleSnakeNextMovePos(int snakeLen, Point p, Session s) {
            ArrayList<Integer> list = s.snakeNextMovePossibleLocationList.get(p);
            if(list == null){
                list = new ArrayList<>();
                s.snakeNextMovePossibleLocationList.put(p, list);
            }
            list.add(snakeLen);
        }

        /**
         * This method is called when a game your Battlesnake was in ends.
         * 
         * It is purely for informational purposes, you don't have to make any decisions
         * here.
         *
         * @param endRequest a map containing the JSON sent to this snake. Use this data
         *                   to know which game has ended
         * @return responses back to the engine are ignored.
         */
        public Map<String, String> end(JsonNode endRequest) {
            LOG.info("END");
            JsonNode game = endRequest.get("game");
            String gameId = game.get("id").asText();
            Session s = sessions.remove(gameId);
            if(s == null){
                s = new Session();
                s.players = new ArrayList<>();
            }

            String gameType = "UNKNOWN";
            if(game.has("ruleset")){
                gameType = game.get("ruleset").get("name").asText().toLowerCase();
            }

            JsonNode board = endRequest.get("board");
            JsonNode haz = board.get("hazards");
            if (haz != null && haz.size()>0) {
                gameType = gameType+" HAZARD";
            }

            // get OWN ID
            JsonNode you = endRequest.get("you");
            String myId = you.get("id").asText();

            // get the locations of all snakes...
            JsonNode snakes = board.get("snakes");
            int sLen = snakes.size();
            if(sLen > 0) {
                for (int i = 0; i < sLen; i++) {
                    JsonNode aSnake = snakes.get(i);
                    if (aSnake.get("id").asText().equals(myId)) {
                        LOG.info("****************");
                        LOG.info("WE ARE ALIVE!!!! ");
                        LOG.info(gameType+" "+s.players.toString());
                        LOG.info(gameId);
                        LOG.info("****************");
                    } else {
                        LOG.info("****************");
                        LOG.info("that's not us... " + aSnake.get("name").asText());
                        LOG.info(gameType+" "+s.players.toString());
                        LOG.info(gameId);
                        LOG.info("****************");
                    }
                }
            } else {
                LOG.info("****************");
                LOG.info("that's not us... ???");
                LOG.info(gameType+" "+s.players.toString());
                LOG.info(gameId);
                LOG.info("****************");
            }
            return EMPTY;
        }
    }

    private void ranomizer(){
        /*if(!patched) {
            if(Math.random() * 20 > 17) {
                patched = true;
                int addon1 = (int) (Math.random() * 3);
                int addon2 = (int) (Math.random() * 3);
                Ymin = addon1;
                Xmin = addon2;
                Ymax = Y - (1+addon1);
                Xmax = X - (1+addon2);
            }
        }*/

        /*int rand = (int) (Math.random() * 20);
        if(rand > 18){
            if(move.equals(U) && pos[0] > 2 && pos[0] < Y-3 ){
                move = R;
            } else if(move.equals(L) && pos[1] > 2 && pos[1] < X-3 ){
                move = U;
            } else if(move.equals(D) && pos[0] > 2 && pos[0] < Y-3 ){
                move = L;
            } else if(move.equals(R) && pos[1] > 2 && pos[1] < X-3 ){
                move = D;
            }
        }*/
    }
}