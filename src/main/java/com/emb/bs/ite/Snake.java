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

    static final int UP = 0;
    static final int RIGHT = 1;
    static final int DOWN = 2;
    static final int LEFT = 3;

    static final String REPEATLAST = "repeat";

    static final String U = "up";
    static final String D = "down";
    static final String L = "left";
    static final String R = "right";

    private static HashMap<Integer, MoveWithState> moveKeyMap = new HashMap<>();

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

        moveKeyMap.put(UP, new MoveWithState(U));
        moveKeyMap.put(RIGHT, new MoveWithState(R));
        moveKeyMap.put(DOWN, new MoveWithState(D));
        moveKeyMap.put(LEFT, new MoveWithState(L));
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
            response.put("color", "#33cc33");
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

                String move = calculateNextMoveOptions(s);

                if(move.equals(REPEATLAST)){
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

                s.logState("=> RESULTING MOVE: "+move, LOG);
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

        /*private String getMoveWithLowestRiskFromAlternatives(Session s, String move, ArrayList<String> altMoves) {
            // since we want to find the move with the lowest risk, we add the initial move
            // so we compare all risks
            altMoves.add(move);

            // comparing RISK of "move" with alternative moves
            int minRisk = Integer.MAX_VALUE;

            for (String aMove : altMoves) {
                Point altPos = null;
                switch (aMove) {
                    case U:
                        altPos = s.getNewPointForDirection(s.myPos, UP);
                        break;
                    case R:
                        altPos = s.getNewPointForDirection(s.myPos, RIGHT);
                        break;
                    case D:
                        altPos = s.getNewPointForDirection(s.myPos, DOWN);
                        break;
                    case L:
                        altPos = s.getNewPointForDirection(s.myPos, LEFT);
                        break;
                }

                if (altPos != null) {
                    int aMoveRisk = s.snakeNextMovePossibleLocations[altPos.y][altPos.x];
                    if (aMoveRisk == 0 && !s.wrappedMode) {
                        // ok no other snake is here in the area - but if we are a move to the BORDER
                        // then consider this move as a more risk move...
                        if (altPos.y == 0 || altPos.y == s.Y - 1) {
                            aMoveRisk++;
                        }
                        if (altPos.x == 0 || altPos.x == s.X - 1) {
                            aMoveRisk++;
                        }
                    }
                    minRisk = Math.min(minRisk, aMoveRisk);
                    if (aMoveRisk == minRisk) {
                        move = aMove;
                    }
                }
            }
            return move;
        }*/

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
            s.myPos = new Point(head);
            // adding also myHead to the body array (to allow
            // simple NoGoZone-Detection
            s.myBody[s.myPos.y][s.myPos.x] = s.myLen;

            JsonNode myBody = you.get("body");
            int myBodyLen = myBody.size()-1;
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
                    /*String fof = aSnake.get("name").asText().toLowerCase();
                    if(!s.hungerMode){// && checkFoF(fof)){
                        s.hungerMode = true;
                    }*/
                    int len = aSnake.get("length").asInt();
                    s.maxOtherSnakeLen = Math.max(len, s.maxOtherSnakeLen);
                    Point h = new Point(aSnake.get("head"));
                    s.snakeBodies[h.y][h.x] = len;
                    s.snakeHeads.add(h);

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
                            newYUp = h.y +1;
                        }
                        if(h.x > 0){
                            newXLeft = h.x - 1;
                        }
                        if(h.x < s.X - 1 ){
                            newXRight = h.x + 1;
                        }
                    }

                    if (newYDown > -1 && s.snakeBodies[newYDown][h.x] == 0) {
                        s.snakeNextMovePossibleLocations[newYDown][h.x] = Math.max(len, s.snakeNextMovePossibleLocations[newYDown][h.x]);
                    }
                    if (newYUp > -1 && s.snakeBodies[newYUp][h.x] == 0) {
                        s.snakeNextMovePossibleLocations[newYUp][h.x] = Math.max(len, s.snakeNextMovePossibleLocations[newYUp][h.x]);
                    }
                    if (newXLeft > -1 && s.snakeBodies[h.y][newXLeft] == 0) {
                        s.snakeNextMovePossibleLocations[h.y][newXLeft] = Math.max(len, s.snakeNextMovePossibleLocations[h.y][newXLeft]);
                    }
                    if (newXRight > -1 && s.snakeBodies[h.y][newXRight] == 0) {
                        s.snakeNextMovePossibleLocations[h.y][newXRight] = Math.max(len, s.snakeNextMovePossibleLocations[h.y][newXRight]);
                    }

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
            s.logState("MOVE CALLED", LOG);
            s.logBoard(LOG);
        }

        private String calculateNextMoveOptions(Session s) {
            int[] bounds = new int[]{s.yMin, s.xMin, s.yMax, s.xMax};
            // checkSpecialMoves will also activate the 'goForFood' flag - so if this flag is set
            // we hat a primary and secondary direction in which we should move in order to find/get
            // food...
            List<Integer> killMoves = s.checkSpecialMoves();

            SortedSet<Integer> options = new TreeSet<Integer>();
            // make sure that we check initially our preferred direction...
            if(s.foodGoForIt) {
                if (s.mFoodPrimaryDirection != -1) {
                    options.add(s.mFoodPrimaryDirection);
                }
                if (s.mFoodSecondaryDirection != -1) {
                    options.add(s.mFoodSecondaryDirection);
                }
            }
            options.add(s.state);
            options.add(UP);
            options.add(RIGHT);
            options.add(DOWN);
            options.add(LEFT);

            ArrayList<MoveWithState> possibleMoves = new ArrayList<>();
            Session.SavedState startState = s.saveState();
            boolean needToResetBounds = false;
            for(int possibleDirection: options){
                s.restoreState(startState);
                if(needToResetBounds) {
                    s.restoreBoardBounds(bounds);
                    needToResetBounds = false;
                }

                if(possibleDirection == s.mFoodPrimaryDirection || possibleDirection == s.mFoodSecondaryDirection){
                    // So depending on the situation we want to take more risks in order to
                    // get FOOD - but this Extra risk should be ONLY applied when making a
                    // food move!
                    if(s.foodFetchConditionGoHazard){
                        s.escapeFromHazard = false;
                        s.enterHazardZone = true;
                    }
                    if(s.foodFetchConditionGoBorder){
                        s.escapeFromBorder = false;
                        s.enterBorderZone = true;
                        s.setFullBoardBounds();
                        needToResetBounds = true;
                    }
                }
                // ok checking the next direction...
                String moveResult = s.moveDirection(possibleDirection, null);
                if(moveResult !=null && !moveResult.equals(REPEATLAST)) {
                    MoveWithState move = new MoveWithState(moveResult, s);
                    possibleMoves.add(move);
                    LOG.info("EVALUATED WE can MOVE: " + move);
                }else{
                    LOG.info("EVALUATED WE can NOT MOVE: " + s.getMoveIntAsString(possibleDirection));
                }
            }

            if(possibleMoves.size() == 0){
                s.doomed = true;
                // TODO Later - check, if any of the moves make still some sense?! [but I don't think so]
                LOG.error("***********************");
                LOG.error("DOOMED!");
                LOG.error("***********************");
                return Snake.REPEATLAST;
            }

            if(possibleMoves.size() == 1){
                return possibleMoves.get(0).move;
            }else{
                return getBestMove(possibleMoves, killMoves, s);
            }
        }

        private String getBestMove(ArrayList<MoveWithState> possibleMoves, List<Integer> killMoves, Session s) {
            // ok we have plenty of alternative moves...
            // we should check, WHICH of them is the most promising...

            // TODO - order moves by RISK-LEVEL!
            // sEnterNoGoZone or sEnterDangerZone should avoided if possible... if we have
            // other alternatives...

            //1) only keep the moves with the highest DEEP...
            int maxDept = 0;
            HashSet<MoveWithState> movesToRemove = new HashSet<>();
            for (MoveWithState aMove : possibleMoves) {
                int dept = aMove.state.sMAXDEEP;
                maxDept = Math.max(maxDept, dept);
            }
            for (MoveWithState aMove : possibleMoves) {
                int dept = aMove.state.sMAXDEEP;
                if (dept < maxDept) {
                    movesToRemove.add(aMove);
                }
            }

            if(movesToRemove.size() >0) {
                possibleMoves.removeAll(movesToRemove);
            }
            if(possibleMoves.size() == 1){
                // ok only one option left - so let's use this...
                return possibleMoves.get(0).move;
            }

            //2) remove all "toDangerous" moves (when we have better alternatives)
            boolean keepGoDanger = true;
            boolean keepGoNoGo = true;
            for (MoveWithState aMove : possibleMoves) {
                if (keepGoNoGo && !aMove.state.sEnterNoGoZone) {
                    keepGoNoGo = false;
                }
                if (keepGoDanger && !aMove.state.sEnterDangerZone) {
                    keepGoDanger = false;
                }
            }
            for (MoveWithState aMove : possibleMoves) {
                if (!keepGoNoGo && aMove.state.sEnterNoGoZone){
                    movesToRemove.add(aMove);
                }
                if (!keepGoDanger && aMove.state.sEnterDangerZone){
                    movesToRemove.add(aMove);
                }
            }
            if(movesToRemove.size() >0) {
                possibleMoves.removeAll(movesToRemove);
            }

            if(possibleMoves.size() == 1){
                // ok only one option left - so let's use this...
                return possibleMoves.get(0).move;
            }


            // checking the possible killMoves...
            if(killMoves != null && killMoves.size() > 0){
                // ok checking possible kills
            }

            if(s.mFoodPrimaryDirection != -1 && s.mFoodSecondaryDirection != -1){
                // TODO: decide for the better FOOD move...
                // checking if primary or secondary FOOD direction is possible
                // selecting the MOVE with less RISK (if there is one with)
                // avoid from border we can do so...
                MoveWithState pMove = moveKeyMap.get(s.mFoodPrimaryDirection);
                if (possibleMoves.contains(pMove)) {
                    return pMove.move;
                }
                MoveWithState sMove = moveKeyMap.get(s.mFoodSecondaryDirection);
                if (possibleMoves.contains(sMove)) {
                    return sMove.move;
                }
            } else if(s.mFoodPrimaryDirection != -1) {
                MoveWithState pMove = moveKeyMap.get(s.mFoodPrimaryDirection);
                if (possibleMoves.contains(pMove)) {
                    return pMove.move;
                }
            }

            // 3) Manual additional risk calculation...
            // comparing RISK of "move" with alternative moves

            // we want our "first" (the preferred) item to be evaluated last...
            //Collections.reverse(possibleMoves);

            TreeMap<Integer, ArrayList<String>> finalMoves = new TreeMap<>();
            for (MoveWithState aMove : possibleMoves) {
                Point resultingPos = null;
                switch (aMove.move) {
                    case U:
                        resultingPos = s.getNewPointForDirection(s.myPos, UP);
                        break;
                    case R:
                        resultingPos = s.getNewPointForDirection(s.myPos, RIGHT);
                        break;
                    case D:
                        resultingPos = s.getNewPointForDirection(s.myPos, DOWN);
                        break;
                    case L:
                        resultingPos = s.getNewPointForDirection(s.myPos, LEFT);
                        break;
                }

                if (resultingPos != null) {
                    // checking if we are under direct threat
                    int aMoveRisk = s.snakeNextMovePossibleLocations[resultingPos.y][resultingPos.x];
                    if (aMoveRisk == 0 && !s.mWrappedMode) {

                        // TODO: not only the BORDER - also the MIN/MAX can be not so smart...

                        // ok no other snake is here in the area - but if we are a move to the BORDER
                        // then consider this move as a more risk move...
                        if (resultingPos.y == 0 || resultingPos.y == s.Y - 1) {
                            aMoveRisk++;
                        }
                        if (resultingPos.x == 0 || resultingPos.x == s.X - 1) {
                            aMoveRisk++;
                        }
                        
                        // if this is not a move into a corner, we should check the distance from other snakes
                        // head's that are LONGER (or have the same length but can catch FOOD with the next move...

                        // calculating the distance to all s.snakeNextMovePossibleLocations... (good to have an
                        // array of all of them) - special handing, if 'snakeNextMovePossibleLocations' is also
                        // a foodPosition! (then a snake tha is currently 1 times shorter becomes equal)
                    }
                    ArrayList<String> moves = finalMoves.get(aMoveRisk);
                    if(moves == null) {
                        moves = new ArrayList<>();
                        finalMoves.put(aMoveRisk, moves);
                    }
                    moves.add(aMove.move);
                }
            }

            // lowest Risk move options...
            ArrayList<String> lowestRiskMoves = finalMoves.firstEntry().getValue();

            // now we can check, if we can follow the default movement plan...
            String finalMove = lowestRiskMoves.get(0);

            boolean canGoUp     = lowestRiskMoves.contains(Snake.UP);
            boolean canGoRight  = lowestRiskMoves.contains(Snake.RIGHT);
            boolean canGoDown   = lowestRiskMoves.contains(Snake.DOWN);
            boolean canGoLeft   = lowestRiskMoves.contains(Snake.LEFT);

            switch (s.state){
                case UP:
                    if(canGoUp) {
                        return Snake.U;
                    } else {
                        if (s.myPos.x < s.xMax / 2 || !canGoLeft){ //cmdChain.contains(Snake.LEFT)) {
                            s.state = Snake.RIGHT;
                            if(canGoRight){
                                return Snake.R;
                            }
                        } else {
                            s.state = Snake.LEFT;
                            if(canGoLeft){
                                return Snake.L;
                            }
                        }
                    }
                    break;

                case RIGHT:
                    if(canGoRight) {
                        return Snake.R;
                    }else{
                        if (s.myPos.x == s.xMax && s.tPhase > 0) {
                            if (s.myPos.y == s.yMax) {
                                // we should NEVER BE HERE!!
                                // we are in the UPPER/RIGHT Corner while in TraverseMode! (something failed before)
                                LOG.info("WE SHOULD NEVER BE HERE in T-PHASE >0");
                                s.tPhase = 0;
                                s.state = Snake.DOWN;
                                //OLD CODE:
                                //return moveDown();
                            } else {
                                s.state = Snake.LEFT;
                                //OLD CODE:
                                //return moveUp();
                            }
                        } else {
                            //OLD CODE:
                            //return decideForUpOrDownUsedFromMoveLeftOrRight(Snake.RIGHT);
                        }
                    }
                    break;

                case DOWN:
                    if(canGoDown){
                        if (s.tPhase == 2 && s.myPos.y == s.yMin + 1) {
                            s.tPhase = 1;
                            s.state = Snake.RIGHT;
                            if(canGoRight){
                                return Snake.R;
                            }
                        } else {
                            return Snake.D;
                        }
                    } else{
                        if (s.tPhase > 0) {
                            s.state = Snake.RIGHT;
                            if(canGoRight){
                                return Snake.R;
                            }
                        } else {
                            if (s.myPos.x < s.xMax / 2 || !canGoLeft) { //cmdChain.contains(Snake.LEFT)) {
                                s.state = Snake.RIGHT;
                                if(canGoRight){
                                    return Snake.R;
                                }
                            } else {
                                s.state = Snake.LEFT;
                                if(canGoLeft){
                                    return Snake.L;
                                }
                            }
                        }

                    }
                    break;

                case LEFT:
                    if(canGoLeft) {

                        // even if we "could" move to left - let's check, if we should/will follow our program...
                        if (s.myPos.x == s.xMin + 1) {
                            // We are at the left-hand "border" side of the board
                            if (s.tPhase != 2) {
                                s.tPhase = 1;
                            }
                            if (s.myPos.y == s.yMax) {
                                s.state = Snake.DOWN;
                                return Snake.L;
                            } else {
                                if (canGoUp) {
                                    s.state = Snake.RIGHT;
                                    return Snake.U;
                                } else {
                                    return Snake.L;
                                }
                            }
                        } else {
                            if ((s.yMax - s.myPos.y) % 2 == 1) {
                                // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                                // we simply really move to the LEFT (since we can!))
                                if (canGoUp) {
                                    s.tPhase = 2;
                                    return Snake.U;
                                } else {
                                    return Snake.L;
                                }
                            } else {
                                return Snake.L;
                            }
                        }


                    } else {

                        // IF we can't go LEFT, then we should check, if we are at our special position
                        // SEE also 'YES' part (only difference is, that we do not MOVE to LEFT here!)
                        if (s.myPos.x == s.xMin + 1) {
                            // We are at the left-hand "border" side of the board
                            if (s.tPhase != 2) {
                                s.tPhase = 1;
                            }
                            if (s.myPos.y == s.yMax) {
                                s.state = Snake.DOWN;
                                //return Snake.L;
                                //OLD CODE:
                                //return moveDown();

                            } else {
                                if (canGoUp) {
                                    s.state = Snake.RIGHT;
                                    return Snake.U;
                                } else {
                                    //return Snake.L;
                                    //OLD CODE:
                                    //return moveDown();
                                }
                            }
                        } else {
                            if ((s.yMax - s.myPos.y) % 2 == 1) {
                                // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                                // we simply really move to the LEFT (since we can!))
                                if (canGoUp) {
                                    s.tPhase = 2;
                                    return Snake.U;
                                } else {
                                    //return Snake.L;
                                    //OLD CODE:
                                    //return moveDown();
                                }
                            } else {
                                // return Snake.L;
                                // if we are in the pending mode, we prefer to go ALWAYS UP
                                //OLD CODE:
                                //return decideForUpOrDownUsedFromMoveLeftOrRight(Snake.LEFT);
                            }
                        }

                    }
                    break;
            }

            return finalMove;
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