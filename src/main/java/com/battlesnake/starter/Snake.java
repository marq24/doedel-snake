package com.battlesnake.starter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

    static final int UP = 0;
    static final int RIGHT = 1;
    static final int DOWN = 2;
    static final int LEFT = 3;

    static final String U = "up";
    static final String D = "down";
    static final String L = "left";
    static final String R = "right";



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
        private HashMap<String, com.battlesnake.starter.Session> sessions = new HashMap();

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
                    LOG.info("{} called with: {}", uri, req.body());
                    snakeResponse = end(parsedRequest);
                } else {
                    throw new IllegalAccessError("Strange call made to the snake: " + uri);
                }
                LOG.info("Responding with: {}", JSON_MAPPER.writeValueAsString(snakeResponse));
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
            response.put("color", "#33FF33");
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
            sessions.put(startRequest.get("game").get("id").asText(), new Session());
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
            Session s = sessions.get(moveRequest.get("game").get("id").asText());

            JsonNode board = moveRequest.get("board");
            if(s.X == -1) {
                s.Y = board.get("height").asInt();
                s.X = board.get("width").asInt();
                s.Ymin = 0;
                s.Xmin = 0;
                s.Ymax = s.Y-1;
                s.Xmax = s.X-1;
            }

            // clearing the used fields...
            s.enemyBodies = new int[s.Y][s.X];
            s.enemyHeads = new int[s.Y][s.X];
            s.myBody = new int[s.Y][s.X];
            s.foodPlaces = new ArrayList<>();

            // get OWN ID
            JsonNode you = moveRequest.get("you");
            String myId = you.get("id").asText();

            // get the locations of all snakes...
            JsonNode snakes = board.get("snakes");
            int slen = snakes.size();
            for (int i = 0; i < slen; i++) {
                JsonNode aSnake = snakes.get(i);
                if(!aSnake.get("id").asText().equals(myId)){
                    int len = aSnake.get("length").asInt();

                    JsonNode body = aSnake.get("body");
                    int blen = body.size();
                    // we start from j=1 here - since we handle the HEAD's
                    // later!!
                    for (int j = 1; j < blen; j++) {
                        Point p = new Point(body.get(j));
                        s.enemyBodies[p.y][p.x] = 1;
                        // playing "avoid other Challenge" we need "myPlaces" in order to
                        // have only one pixel distance...
                        //myPlaces[p.y][p.x] = 1;
                    }

                    JsonNode head = aSnake.get("head");
                    Point h = new Point(head);
                    s.enemyHeads[h.y][h.x] = len;
                    try {
                        if(s.enemyBodies[h.y - 1][h.x] == 0) {
                            s.enemyHeads[h.y - 1][h.x] = 1;
                        }
                    } catch (IndexOutOfBoundsException e) {}
                    try {
                        if(s.enemyBodies[h.y + 1][h.x] == 0) {
                            s.enemyHeads[h.y + 1][h.x] = 1;
                        }
                    } catch (IndexOutOfBoundsException e) {}
                    try {
                        if(s.enemyBodies[h.y][h.x - 1] == 0) {
                            s.enemyHeads[h.y][h.x - 1] = 1;
                        }
                    } catch (IndexOutOfBoundsException e) {}
                    try {
                        if(s.enemyBodies[h.y][h.x + 1] == 0) {
                            s.enemyHeads[h.y][h.x + 1] = 1;
                        }
                    } catch (IndexOutOfBoundsException e) {}


                    s.enemyBodies[h.y][h.x] = len;
                    /*try {
                        s.enemyBodies[h.y - 1][h.x] = 1;
                    } catch (IndexOutOfBoundsException e) {
                    }
                    try {
                        s.enemyBodies[h.y + 1][h.x] = 1;
                    } catch (IndexOutOfBoundsException e) {
                    }
                    try {
                        s.enemyBodies[h.y][h.x - 1] = 1;
                    } catch (IndexOutOfBoundsException e) {
                    }
                    try {
                        s.enemyBodies[h.y][h.x + 1] = 1;
                    } catch (IndexOutOfBoundsException e) {
                    }*/
                }
            }

            JsonNode haz = board.get("hazards");
            if(haz != null){
                int hlen = haz.size();
                for (int i = 0; i < hlen; i++) {
                    Point p = new Point(haz.get(i));
                    s.myBody[p.y][p.x] = 1;
                }
            }

            JsonNode food = board.get("food");
            if(food != null) {
                int flen = food.size();
                for (int i = 0; i < flen; i++) {
                    s.foodPlaces.add(new Point(food.get(i)));
                }
            }

            String move = D;
            s.health = you.get("health").asInt();
            s.len = you.get("length").asInt();
            JsonNode body = you.get("body");
            int blen = body.size();
            for (int i=1; i<blen; i++){
                Point p = new Point(body.get(i));
                s.myBody[p.y][p.x] = 1;
            }
            JsonNode head = you.get("head");
            s.pos = new Point(head);

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

//            boolean huntForFood = false;
//            if(health == 100){
//                // ok back on previous track!
//                Ymin = 0;
//                Xmin = 0;
//                Ymax = Y-1;
//                Xmax = X-1;
//
//                // after we got our food we need to find the closest wall (again?)
//                // 1 | 2
//                // -----
//                // 3 | 4
//                /*if( pos.x > Xmax/2 && pos.y > Ymax/2){
//                    // -> we are IN Q2
//                    if(pos.x > pos.y){
//                        // we should move RIGHT
//                        state = 1;
//                    }else{
//                        // we should move UP...
//                        state = 0;
//                    }
//                } else if( pos.x <= Xmax/2 && pos.y <= Ymax/2){
//                    // -> we are IN Q3
//                    if(pos.x > pos.y){
//                        // we should move LEFT
//                        state = 3;
//                    }else{
//                        // we should move DOWN...
//                        state = 2;
//                    }
//                } else if(pos.x <= Xmax/2){
//                    // we are in Q1
//                    state = 1;
//                }else{
//                    // we are in Q4
//                    state = 3;
//                }*/
//                if(pos.x <= Xmax/2){
//                    state = LEFT;
//                }else if( pos.y <= Ymax/2){
//                    state = DOWN;
//                }else if( pos.x > pos.y ) {
//                    state = RIGHT;
//                }else{
//                    state = UP;
//                }
//
//                //state = stateToRestore;
//
//            } else if(health < 50){
//                P closestFood = null;
//                int minDist = Integer.MAX_VALUE;
//                for(P f: foodPlaces){
//                    int dist = Math.abs( f.x - pos.x) + Math.abs( f.y - pos.y);
//                    minDist = Math.min(minDist, dist);
//                    if(minDist == dist){
//                        closestFood = f;
//                    }
//                }
//
//                if(closestFood != null){
//                    Ymin = closestFood.y;
//                    Xmin = closestFood.x;
//                    Ymax = closestFood.y;
//                    Xmax = closestFood.x;
//                    stateToRestore = state;
//                    huntForFood = true;
//                    LOG.info("GOTO: -> "+closestFood);
//                }
//            }


            boolean huntForFood = false;
            switch (s.state){
                case UP:
                    move = s.moveUp(0, !huntForFood);
                    break;
                case RIGHT:
                    move = s.moveRight(0, !huntForFood);
                    break;
                case DOWN:
                    move = s.moveDown(0, !huntForFood);
                    break;
                case LEFT:
                    move = s.moveLeft(0, !huntForFood);
                    break;
            }

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

            //JsonNode head = moveRequest.get("you").get("head");
            //JsonNode body = moveRequest.get("you").get("body");


            // Don't allow your Battlesnake to move back in on it's own neck
            //avoidMyNeck(head, body, possibleMoves);

            // TODO: Using information from 'moveRequest', find the edges of the board and
            // don't
            // let your Battlesnake move beyond them board_height = ? board_width = ?

            // TODO Using information from 'moveRequest', don't let your Battlesnake pick a
            // move
            // that would hit its own body

            // TODO: Using information from 'moveRequest', don't let your Battlesnake pick a
            // move
            // that would collide with another Battlesnake

            // TODO: Using information from 'moveRequest', make your Battlesnake move
            // towards a
            // piece of food on the board

            // Choose a random direction to move in
            //final int choice = new Random().nextInt(possibleMoves.size());
            //final String move = possibleMoves.get(choice);

            //LOG.info("MOVE {}", move);

            Map<String, String> response = new HashMap<>();
            response.put("move", move);
            return response;
        }

        /*private String moveUpOrRight(P pos) {
            if (pos.y < Ymax && usedPlaces[pos.y + 1][pos.x] == 0) {
                return U;
            } else if (pos.x < Xmax && usedPlaces[pos.y][pos.x + 1] == 0) {
                return R;
            } else {
                state = DOWN;
                patched = false;
                Ymax = Y - 1;
                Xmax = X - 1;
                return moveDownOrLeft(pos);
            }
        }

        private String moveDownOrLeft(P pos) {
            if (pos.y > Ymin && usedPlaces[pos.y - 1][pos.x] == 0) {
                return D;
            } else if (pos.x > Xmin && usedPlaces[pos.y][pos.x - 1] == 0) {
                return L;
            } else {
                state = UP;
                patched = false;
                Ymin = 0;
                Xmin = 0;
                return moveUpOrRight(pos);
            }
        }*/


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
            sessions.remove(endRequest.get("game").get("id").asText());
            return EMPTY;
        }
    }
}
