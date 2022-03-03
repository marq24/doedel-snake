package com.emb.bs.ite;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Session {

    private static final HashMap<Integer, MoveWithState> moveKeyMap = new HashMap<>();
    static{
        moveKeyMap.put(Snake.UP, new MoveWithState(Snake.U));
        moveKeyMap.put(Snake.RIGHT, new MoveWithState(Snake.R));
        moveKeyMap.put(Snake.DOWN, new MoveWithState(Snake.D));
        moveKeyMap.put(Snake.LEFT, new MoveWithState(Snake.L));
    }

    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    // just for logging...
    ArrayList<String> players;
    String gameId;
    int turn;

    // stateful stuff
    private int tPhase = 0;
    private int state = Snake.UP;
    private int mFoodPrimaryDirection = -1;
    private int mFoodSecondaryDirection = -1;

    // Food direction stuff
    private Point foodActive = null;
    private boolean foodGoForIt = false;
    private boolean foodFetchConditionGoHazard = false;
    private boolean foodFetchConditionGoBorder = false;

    String LASTMOVE = null;

    Point myHead;
    Point myTail;
    int myLen;
    int myHealth;

    int X = -1;
    int Y = -1;
    private int xMin, yMin, xMax, yMax;

    private boolean doomed = false;
    ArrayList<Point> snakeHeads = null;
    int[][] snakeBodies = null;
    int[][] snakeNextMovePossibleLocations = null;
    int maxOtherSnakeLen = 0;
    int[][] myBody = null;
    int[][] hazardZone = null;
    ArrayList<Point> foodPlaces = null;

    private int MAXDEEP = 0;
    private boolean enterHazardZone = false;
    private boolean enterBorderZone = false;
    private boolean enterDangerZone = false;
    private boolean enterNoGoZone = false;

    private boolean escapeFromBorder = false;
    private boolean escapeFromHazard = false;


    private boolean mHungerMode = true;

    boolean mWrappedMode = false;
    private boolean mRoyaleMode = false;
    private boolean mHazardPresent = false;

    class SavedState {
        int sState = state;
        int sTPhase = tPhase;
        boolean sEscapeFromBorder = escapeFromBorder;
        boolean sEscapeFromHazard = escapeFromHazard;
        boolean sEnterHazardZone = enterHazardZone;
        boolean sEnterBorderZone = enterBorderZone;
        boolean sEnterDangerZone = enterDangerZone;
        boolean sEnterNoGoZone = enterNoGoZone;
        int sMAXDEEP = MAXDEEP;

        @Override
        public String toString() {
                return
                      " st:" + getMoveIntAsString(state).substring(0, 2).toUpperCase() + "[" + sState + "]"
                    + " ph:" + sTPhase
                    + (sEscapeFromHazard ? " GETOUTHAZD" : "")
                    + (mHazardPresent ? " goHazd? " + sEnterHazardZone : "")
                    + (sEscapeFromBorder ? " GAWYBRD" : "")
                    + " maxDeep? " + sMAXDEEP
                    + " goBorder? " + sEnterBorderZone
                    + " goDanger? " + sEnterDangerZone
                    + " goNoGo? " + sEnterNoGoZone;
        }
    }

    SavedState saveState() {
        return new SavedState();
    }

    void restoreState(SavedState savedState) {
        state = savedState.sState;
        tPhase = savedState.sTPhase;

        escapeFromBorder = savedState.sEscapeFromBorder;
        escapeFromHazard = savedState.sEscapeFromHazard;
        enterHazardZone = savedState.sEnterHazardZone;
        enterBorderZone = savedState.sEnterBorderZone;
        MAXDEEP = savedState.sMAXDEEP;
        enterDangerZone = savedState.sEnterDangerZone;
        enterNoGoZone = savedState.sEnterNoGoZone;
    }

    private void setFullBoardBounds() {
        yMin = 0;
        xMin = 0;
        yMax = Y - 1;
        xMax = X - 1;
    }

    private void restoreBoardBounds(int[] prevBounds) {
        yMin = prevBounds[0];
        xMin = prevBounds[1];
        yMax = prevBounds[2];
        xMax = prevBounds[3];
    }

    private void initSaveBoardBounds() {
        yMin = 1;
        xMin = 1;
        yMax = Y - 2;
        xMax = X - 2;
        enterDangerZone = false;
        enterNoGoZone = false;
        enterBorderZone = false;
        enterHazardZone = false;
    }

    void initSessionForTurn(String gameType, int height, int width) {
        Y = height;
        X = width;
        initSaveBoardBounds();
        doomed = false;
        mHazardPresent = false;

        //firstMoveToTry = -1;
        //cmdChain = new ArrayList<>();

        snakeHeads = new ArrayList<>();
        snakeBodies = new int[Y][X];
        snakeNextMovePossibleLocations = new int[Y][X];
        maxOtherSnakeLen = 0;

        myBody = new int[Y][X];

        // TODO: MAXDEEP can be myLen-1 IF THERE IS NO FODD in front of us
        // really???
        MAXDEEP = myLen;//Math.min(len, 20);

        foodGoForIt = false;
        foodFetchConditionGoHazard = false;
        foodFetchConditionGoBorder = false;

        foodPlaces = new ArrayList<>();

        hazardZone = new int[Y][X];
        //hazardNearbyPlaces = new ArrayList<>();

        escapeFromBorder = false;
        escapeFromHazard = false;

        if(gameType != null) {
            switch (gameType) {
                case "solo":
                case "standard":
                case "squad":
                    break;

                case "royale":
                    mRoyaleMode = true;
                    mHungerMode = false;
                    break;

                case "wrapped":
                    mWrappedMode = true;
                    enterBorderZone = true;
                    setFullBoardBounds();
                    break;

                case "constrictor":
                    // NOT sure yet, if moving totally
                    // to the border is smart...
                    enterBorderZone = true;
                    mHungerMode = false;
                    setFullBoardBounds();
                    break;
            }
        }else{
            // no game mode provided? [do we read from a REPLAY?!]
        }
    }

    void initSessionAfterFullBoardRead(boolean hazardDataIsPresent) {
        // before we check any special moves, we check, if we are already on the borderline, and if this is the
        // case we can/will disable 'avoid borders' flag...

        if (!enterBorderZone) {
            if (    myHead.y == 0 ||
                    myHead.y == Y - 1 ||
                    myHead.x == 0 ||
                    myHead.x == X - 1
            ) {
                escapeFromBorder = true;
            }
        }

        mHazardPresent = hazardDataIsPresent;
        if(mHazardPresent) {
            if (!enterHazardZone) {
                if (hazardZone[myHead.y][myHead.x] > 0 && myHealth < 95) {
                    escapeFromHazard = true;
                }
            }

            // try to adjust the MIN/MAX values based on the present hazardData...
            if(mRoyaleMode){
                ArrayList<Boolean>[] yAxisHazards = new ArrayList[Y];
                ArrayList<Boolean>[] xAxisHazards = new ArrayList[X];

                for (int y = 0; y < Y; y++) {
                    for (int x = 0; x < X; x++) {
                        if(hazardZone[y][x] == 1){
                            if(yAxisHazards[y] == null){
                                yAxisHazards[y] = new ArrayList<>(Y);
                            }
                            yAxisHazards[y].add(true);

                            if(xAxisHazards[x] == null){
                                xAxisHazards[x] = new ArrayList<>(X);
                            }
                            xAxisHazards[x].add(true);
                        }
                    }
                }

                for (int y = 0; y < yAxisHazards.length; y++) {
                    if(yAxisHazards[y] != null && yAxisHazards[y].size() == Y){
                        if(y < Y/2){
                            yMin = y + 1;
                        }else if(y> Y/2){
                            yMax = y - 1;
                            break;
                        }
                    }
                }

                for (int x = 0; x < xAxisHazards.length; x++) {
                    if(xAxisHazards[x] != null && xAxisHazards[x].size() == X){
                        if(x < X/2){
                            xMin = x + 1;
                        }else if(x > X/2){
                            xMax = x - 1;
                            break;
                        }
                    }
                }
                LOG.info("For: Tn:"+turn+ "-> ADJUSTED MIN/MAX cause of HAZARD TO Y:"+yMin+"-"+yMax+" and X:"+xMin+"-"+xMax);
            }

        }else{
            // there is no hazard  so we can skip the check in the array...
            enterHazardZone = true;
        }
    }

    private class RiskState {
        boolean endReached = false;
        boolean retry = true;

        private void next(){
            retry = true;
            if (escapeFromBorder) {
                LOG.debug("deactivate ESCAPE-FROM-BORDER");
                escapeFromBorder = false;
            } else if(escapeFromHazard){
                LOG.debug("deactivate ESCAPE-FROM-HAZARD");
                escapeFromHazard = false;
            } else if (!enterBorderZone) {
                LOG.debug("activate now GO-TO-BORDERS");
                enterBorderZone = true;
                setFullBoardBounds();
            } else if (mHazardPresent && !enterHazardZone) {
                LOG.debug("activate now GO-TO-HAZARD");
                enterHazardZone = true;
            } else if(MAXDEEP > 1){
                LOG.debug("activate MAXDEEP TO: "+MAXDEEP);
                MAXDEEP--;
            } else if (!enterDangerZone) {
                LOG.debug("activate now GO-TO-DANGER-ZONE");
                enterDangerZone = true;
            } else if (!enterNoGoZone) {
                LOG.debug("activate now GO-TO-NO-GO-ZONE");
                enterNoGoZone = true;
            } else {
                LOG.debug("NO-WAY-TO-MOVE");
                endReached = true;
                retry = false;
            }
        }
    }

    private String moveDirection(int move, RiskState risk) {

        String moveAsString = getMoveIntAsString(move);
        if(risk == null){
            risk = new RiskState();
        }else{
            risk.next();
        }
        if (risk.endReached) {
            return Snake.REPEATLAST;
        } else if (risk.retry) {
            //logState(moveAsString);
            boolean canMove = false;

            switch (move){
                case Snake.UP:
                    canMove = canMoveUp();
                    break;
                case Snake.RIGHT:
                    canMove = canMoveRight();
                    break;
                case Snake.DOWN:
                    canMove = canMoveDown();
                    break;
                case Snake.LEFT:
                    canMove = canMoveLeft();
                    break;
            }
            if (canMove) {
                LOG.debug(moveAsString+": YES");
                return moveAsString;
            }else{
                LOG.debug(moveAsString+": NO");
                return moveDirection(move, risk);
            }
        }
        return null;
    }

    private int getAdvantage(){
        if(mHungerMode){
            return 8;
        } else {
            // how many foods-ahead we want to be...
            // is "one" really just enough?
            int advantage = 2;
            if (myLen > 19) {
                advantage++;
            }
            if (myLen > 24) {
                advantage++;
            }
            if (myLen > 29) {
                advantage++;
            }
            if (myLen > 39) {
                advantage++;
            }
            return advantage;
        }
    }

    private List<Integer> checkSpecialMoves() {
        List<Integer> killMoves = checkKillMoves();
        if(killMoves != null && killMoves.size() >0){
            LOG.info("FOUND possible KILLs :" +killMoves);
        }

        if (myHealth < 31 || (myLen - getAdvantage() <= maxOtherSnakeLen)) {
            LOG.info("Check for FOOD! health:" + myHealth + " len:" + myLen +"(-"+getAdvantage()+")"+ "<=" + maxOtherSnakeLen);
            // ok we need to start to fetch FOOD!
            // we should move into the direction of the next FOOD! (setting our preferred direction)
            checkFoodMoves();
        }else{
            // need to reset all food parameters...
            resetFoodStatus();
        }
        return killMoves;
    }

    private List<Integer> checkKillMoves(){
        // verify if this IF condition makes sense here - we might want to decide later, IF we are going to
        // make the killMove...
        if(myHealth > 19 && (mWrappedMode || myHead.y != 0 && myHead.x !=0 && myHead.y != Y-1 && myHead.x != X-1)) {
            ArrayList<Integer> checkedKills = new ArrayList<>();
            checkForPossibleKillInDirection(Snake.UP, checkedKills);
            checkForPossibleKillInDirection(Snake.RIGHT, checkedKills);
            checkForPossibleKillInDirection(Snake.DOWN, checkedKills);
            checkForPossibleKillInDirection(Snake.LEFT, checkedKills);
            return checkedKills;
        }
        return null;
    }

    private void checkForPossibleKillInDirection(int move, ArrayList<Integer> resList) {
        Point p = getNewPointForDirection(myHead, move);
        try {
            if(myBody[p.y][p.x] == 0 && snakeBodies[p.y][p.x] == 0) {
                int val = snakeNextMovePossibleLocations[p.y][p.x];
                if (val > 0 && val < myLen) {
                    resList.add(move);
                }
            }
        }catch(IndexOutOfBoundsException e){
            // TODO: check for indexOutOfBounds (p.y/x can be -1 or > Y/X) - right now catching the exception
            // cause of laziness
        }
    }

    private void checkFoodMoves() {
        Point closestFood = null;
        int minDist = Integer.MAX_VALUE;

        // we remove all food's that are in direct area of other snakes heads
        // I don't want to battle for food with others (now)
        ArrayList<Point> availableFoods = new ArrayList<>(foodPlaces.size());
        availableFoods.addAll(foodPlaces);

        if (myHealth > 15) {
            // in wrappedMode there are no corners... and in the first 5 turns we might pick
            // up food that is around us...
            if(turn > 5 && !mWrappedMode) {
                // food in CORNERS is TOXIC (but if we are already IN the corner we will
                // take it!
                if (!(myHead.x == 0 && myHead.y <= 1) || (myHead.x <= 1 && myHead.y == 0)) {
                    availableFoods.remove(new Point(0, 0));
                }
                if (!(myHead.x == X - 1 && myHead.y <= 1) || (myHead.x <= X - 2 && myHead.y == 0)) {
                    availableFoods.remove(new Point(0, X - 1));
                }
                if (!(myHead.x == 0 && myHead.y <= Y - 2) || (myHead.x <= 1 && myHead.y == Y - 1)) {
                    availableFoods.remove(new Point(Y - 1, 0));
                }
                if (!(myHead.x == X - 1 && myHead.y >= Y - 2) || (myHead.x >= X - 2 && myHead.y == Y - 1)) {
                    availableFoods.remove(new Point(Y - 1, X - 1));
                }
            }
            for (Point h : snakeHeads) {
                // food that is head of another snake that is longer or has
                // the same length should be ignored...
                if (snakeBodies[h.y][h.x] >= myLen){
                    availableFoods.remove(new Point(h.y + 1, h.x + 1));
                    availableFoods.remove(new Point(h.y + 1, h.x + 0));
                    availableFoods.remove(new Point(h.y + 0, h.x + 1));
                }
            }
        }

        TreeMap<Integer, ArrayList<Point>> foodTargetsByDistance = new TreeMap<>();
        for (Point f : availableFoods) {
            int dist = getFoodDistance(f, myHead);
            if(!isLocatedAtBorder(f) || dist < 3 || (dist < 4 && myHealth < 65) || myHealth < 16) {
                boolean addFoodAsTarget = true;
                for (Point h : snakeHeads) {
                    int otherSnakesDist = getFoodDistance(f, h);
                    boolean otherIsStronger = snakeBodies[h.y][h.x] >= myLen;
                    if(dist > otherSnakesDist || (dist == otherSnakesDist && otherIsStronger)) {
                        addFoodAsTarget = false;
                        break;
                    }
                }
                if(addFoodAsTarget){
                    ArrayList<Point> foodsInDist = foodTargetsByDistance.get(dist);
                    if(foodsInDist == null){
                        foodsInDist = new ArrayList<>();
                        foodTargetsByDistance.put(dist, foodsInDist);
                    }
                    foodsInDist.add(f);
                }
            }
        }

        if(foodTargetsByDistance.size() > 0){
            // get the list of the closest food...
            ArrayList<Point> closestFoodList = foodTargetsByDistance.firstEntry().getValue();
            if(closestFoodList.size() == 1){
                // cool only one
                closestFood = closestFoodList.get(0);
            } else {
                // ok we have to decide which of the foods in the same distance can be caught
                // most easily
                int minBlocks = Integer.MAX_VALUE;

                // ok take the first as default...
                closestFood = closestFoodList.get(0);

                // TODO: count blockingBlocks in WRAPPED MODE
                if(!mWrappedMode) {
                    // need to decided which food is better?!
                    for (Point cfp : closestFoodList) {
                        int blocks = countBlockingsBetweenFoodAndHead(cfp);
                        minBlocks = Math.min(minBlocks, blocks);
                        if (minBlocks == blocks) {
                            closestFood = cfp;
                        } else {
                            LOG.info("FOOD at " + cfp + " blocked by " + blocks + " - stay on: " + closestFood + "(blocked by " + minBlocks + ")");
                        }
                    }
                }
            }

            if(foodActive == null || !foodActive.equals(closestFood)){
                mFoodPrimaryDirection = -1;
                mFoodSecondaryDirection = -1;
            }
            foodActive = closestFood;

            int yDelta = myHead.y - closestFood.y;
            int xDelta = myHead.x - closestFood.x;
            int preferredYDirection = -1;
            int preferredXDirection = -1;
            if (mFoodPrimaryDirection == -1 || yDelta == 0 || xDelta == 0) {
                if(mWrappedMode && Math.abs(yDelta) > Y/2) {
                    preferredYDirection = Snake.UP;
                } else if (yDelta > 0) {
                    preferredYDirection = Snake.DOWN;
                } else if (yDelta < 0) {
                    preferredYDirection = Snake.UP;
                }

                if((mWrappedMode && Math.abs(xDelta) > X/2)){
                    preferredXDirection = Snake.RIGHT;
                }else if (xDelta > 0) {
                    preferredXDirection = Snake.LEFT;
                } else if (xDelta < 0){
                    preferredXDirection = Snake.RIGHT;
                }

                if (Math.abs(yDelta) > Math.abs(xDelta)) {
                    mFoodPrimaryDirection = preferredYDirection;
                    mFoodSecondaryDirection = preferredXDirection;
                } else {
                    mFoodPrimaryDirection = preferredXDirection;
                    mFoodSecondaryDirection = preferredYDirection;
                }
            }

            foodGoForIt = true;
            // IF we are LOW on health, and HAZARD is enabled - we skip the hazard check!
            boolean goFullBorder = false;
            if(!enterHazardZone || escapeFromHazard) {
                // two move in hazard takes 2 x 16 health (we need at least 32 health left)
                if ((myHealth < 34 || (mRoyaleMode && myHealth < 80))
                        &&  (   (xDelta == 0 && Math.abs(yDelta) <= 2) ||
                                (yDelta == 0 && Math.abs(xDelta) <= 2) ||
                                (Math.abs(yDelta) == 1 && Math.abs(xDelta) == 1)
                            )
                ) {
                    foodFetchConditionGoHazard = true;
                    goFullBorder = true;
                }
            }
            if (!enterBorderZone || escapeFromBorder) {
                // 1) when GoHazard triggered...
                // 2) for the first 30 turns we can stay on border...
                // 3) when our length is smaller than 15
                // 4) when we are one smaller than the largest other
                // 5) when the food we want to fetch is at BORDER
                if(goFullBorder || turn < 30 || myLen < 15 || myLen - 1 < maxOtherSnakeLen || isLocatedAtBorder(closestFood)){
                    foodFetchConditionGoBorder = true;
                }
            }

            if(mFoodSecondaryDirection != -1){
                LOG.info("TRY TO GET FOOD: at: " + closestFood + " moving: " + getMoveIntAsString(mFoodPrimaryDirection) +" or "+getMoveIntAsString(mFoodSecondaryDirection));
            }else {
                LOG.info("TRY TO GET FOOD: at: " + closestFood + " moving: " + getMoveIntAsString(mFoodPrimaryDirection));
            }
        } else {
            resetFoodStatus();
            LOG.info("NO NEARBY FOOD FOUND minDist:" + minDist + " x:" + (X / 3) + "+y:" + (Y / 3) + "=" + ((X / 3) + (Y / 3)));
        }
    }
    private void resetFoodStatus() {
        foodGoForIt = false;
        foodFetchConditionGoHazard = false;
        foodFetchConditionGoBorder = false;
        foodActive = null;
        mFoodPrimaryDirection = -1;
        mFoodSecondaryDirection = -1;
    }

    private int getFoodDistance(Point food, Point other){
        if(!mWrappedMode){
            return Math.abs(food.x - other.x) + Math.abs(food.y - other.y);
        }else{
            // in wrappedMode: if food.x = 0 & other.x = 11, then distance is 1
            return Math.min(Math.abs(food.x + X - other.x), Math.abs(food.x - other.x)) + Math.min(Math.abs(food.y + Y - other.y), Math.abs(food.y - other.y));
        }
    }

    private int countBlockingsBetweenFoodAndHead(Point cfp) {
        try {
            int blocks = 0;
            int yDelta = myHead.y - cfp.y;
            int xDelta = myHead.x - cfp.x;
            if (Math.abs(yDelta) > Math.abs(xDelta)) {
                if (yDelta > 0) {
                    // we need to go DOWN to the food...
                    for (int i = cfp.y + 1; i < myHead.y; i++) {
                        if (myBody[i][myHead.x] > 0 || snakeBodies[i][myHead.x] > 0) {
                            blocks++;
                        }
                    }
                } else {
                    // we need to go UP to the food...
                    for (int i = myHead.y + 1; i < cfp.y; i++) {
                        if (myBody[i][myHead.x] > 0 || snakeBodies[i][myHead.x] > 0) {
                            blocks++;
                        }
                    }
                }
            } else {
                if (xDelta > 0) {
                    // we need to go LEFT to the food...
                    for (int i = cfp.x + 1; i < myHead.x; i++) {
                        if (myBody[myHead.y][i] > 0 || snakeBodies[myHead.y][i] > 0) {
                            blocks++;
                        }
                    }
                } else {
                    // we need to go RIGHT to the food...
                    for (int i = myHead.x + 1; i < cfp.x; i++) {
                        if (myBody[myHead.y][i] > 0 || snakeBodies[myHead.y][i] > 0) {
                            blocks++;
                        }
                    }
                }
            }
            return blocks;
        }catch(IndexOutOfBoundsException e){
            LOG.error("IoB when try to count blocking... ");
            return Integer.MAX_VALUE;
        }
    }

    private boolean isLocatedAtBorder(Point p) {
        if(turn < 20 || mWrappedMode){
            return  false;//hazardNearbyPlaces.contains(p);
        }else {
            if(turn < 50 || myLen < 15 || myLen - 1 < maxOtherSnakeLen){
                return  p.y == 0
                        || p.y == Y-1
                        || p.x == 0
                        || p.x == X - 1
                        //|| hazardNearbyPlaces.contains(p)
                        ;
            }else {
                return p.y <= yMin
                        || p.y >= yMax
                        || p.x <= xMin
                        || p.x >= xMax
                        //|| hazardNearbyPlaces.contains(p)
                        ;
            }
        }
    }

    Point getNewPointForDirection(Point aPos, int move){
        Point newPos = aPos.clone();
        if(mWrappedMode) {
            switch (move) {
                case Snake.UP:
                    newPos.y = (newPos.y + 1) % Y;
                    break;
                case Snake.RIGHT:
                    newPos.x = (newPos.x + 1) % X;
                    break;
                case Snake.DOWN:
                    newPos.y = (newPos.y - 1 + Y) % Y;//newPos.y > 0 ? newPos.y - 1 : Y - 1;
                    break;
                case Snake.LEFT:
                    newPos.x = (newPos.x -1 + X) % X;//newPos.x > 0 ? newPos.x - 1 : X - 1;
                    break;
            }
        }else{
            switch (move) {
                case Snake.UP:
                    newPos.y++;
                    break;
                case Snake.RIGHT:
                    newPos.x++;
                    break;
                case Snake.DOWN:
                    newPos.y--;
                    break;
                case Snake.LEFT:
                    newPos.x--;
                    break;
            }
        }
        return newPos;
    }

    private boolean willCreateLoop(int move, Point aPos, int[][] finalMap, int count) {
        // OK we have to check, if with the "planed" next move we will create a closed loop structure (either
        // with ourselves, with the border or with any enemy...
        // when we reach our own tail, then we will fit into the hole for sure!
        try {
            count++;
            if(count <= MAXDEEP) {
                Point newPos = getNewPointForDirection(aPos, move);
                if(newPos.equals(myTail)){
                    return false;
                }
                    // simple check, if we can move from the new position to any other location

                // so in the finalMap we have the picture of the MOVE RESULT
                if(finalMap == null) {
                    finalMap = new int[Y][X];
                    finalMap[myHead.y][myHead.x] = 1;
                    for (int y = 0; y < X; y++) {
                        for (int x = 0; x < X; x++) {
                            if (myTail.y == y && myTail.x == x) {
                                finalMap[y][x] = 2; // the GOLDEN ASS
                            }else if (myBody[y][x] > 0) {
                                finalMap[y][x] = 1;
                            } else if (snakeBodies[y][x] > 0) {
                                finalMap[y][x] = 1;
                            } else if (snakeNextMovePossibleLocations[y][x] > 0) {
                                finalMap[y][x] = 1;
                            }
                        }
                    }
                }
                finalMap[newPos.y][newPos.x] = 1;

//if(turn == 32){logMap(finalMap, count);}

                boolean noUP = !canMoveUp(newPos, finalMap, count);
                boolean noDW = !canMoveDown(newPos, finalMap, count);
                boolean noLF = !canMoveLeft(newPos, finalMap, count);
                boolean noRT = !canMoveRight(newPos, finalMap, count);

                if (noUP && noDW && noLF && noRT) {
                    return true;
                }
            }

        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ willCreateLoop " + getMoveIntAsString(move) + " check...", e);
        }
        return false;
    }

    private boolean canMoveUp() {
        try {
            if (escapeFromBorder && (myHead.x == 0 || myHead.x == X - 1)) {
                return false;
            } else {
                int newY = (myHead.y + 1) % Y;
                return  (mWrappedMode || myHead.y < yMax)
                        && myBody[newY][myHead.x] == 0
                        && snakeBodies[newY][myHead.x] == 0
                        && (!escapeFromHazard || hazardZone[newY][myHead.x] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[newY][myHead.x] == 0)
                        && (enterDangerZone || snakeNextMovePossibleLocations[newY][myHead.x] < myLen)
                        && (enterNoGoZone || !willCreateLoop(Snake.UP, myHead, null,0));
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveUp check...", e);
            return false;
        }
    }

    private boolean canMoveUp(Point aPos, int[][] map, int c) {
        try {
            int newY = (aPos.y + 1) % Y;
            return  (mWrappedMode || aPos.y < yMax)
                    && (map[newY][aPos.x] == 0 || map[newY][aPos.x] == 2)
                    && (enterNoGoZone || !willCreateLoop(Snake.UP, aPos, map, c))
                    ;
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveUpLoop check...", e);
            return false;
        }
    }

    private boolean canMoveRight() {
        try {
            if (escapeFromBorder && (myHead.y == 0 || myHead.y == Y - 1)) {
                return false;
            } else {
                int newX = (myHead.x + 1) % X;
                return  (mWrappedMode || myHead.x < xMax)
                        && myBody[myHead.y][newX] == 0
                        && snakeBodies[myHead.y][newX] == 0
                        && (!escapeFromHazard || hazardZone[myHead.y][newX] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[myHead.y][newX] == 0)
                        && (enterDangerZone || snakeNextMovePossibleLocations[myHead.y][newX] < myLen)
                        && (enterNoGoZone || !willCreateLoop(Snake.RIGHT, myHead, null, 0))
                        ;
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveRight check...", e);
            return false;
        }
    }

    private boolean canMoveRight(Point aPos, int[][] map, int c) {
        try {
            int newX = (aPos.x + 1) % X;
            return  (mWrappedMode || aPos.x < xMax)
                    && (map[aPos.y][newX] == 0 || map[aPos.y][newX] == 2)
                    && (enterNoGoZone || !willCreateLoop(Snake.RIGHT, aPos, map, c))
                    ;
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveRightLoop check...", e);
            return false;
        }
    }

    private boolean canMoveDown() {
        try {
            if (escapeFromBorder && (myHead.x == 0 || myHead.x == X - 1)) {
                return false;
            } else {
                int newY = (myHead.y - 1 + Y) % Y;//myPos.y > 0 ? myPos.y - 1 : Y-1;
                return  (mWrappedMode || myHead.y > yMin)
                        && myBody[newY][myHead.x] == 0
                        && snakeBodies[newY][myHead.x] == 0
                        && (!escapeFromHazard || hazardZone[newY][myHead.x] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[newY][myHead.x] == 0)
                        && (enterDangerZone || snakeNextMovePossibleLocations[newY][myHead.x] < myLen)
                        && (enterNoGoZone || !willCreateLoop(Snake.DOWN, myHead, null, 0))
                        ;
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveDown check...", e);
            return false;
        }
    }

    private boolean canMoveDown(Point aPos, int[][] map, int c) {
        try {
            int newY = (aPos.y - 1 + Y) % Y; // aPos.y > 0 ? aPos.y - 1 : Y-1;
            return  (mWrappedMode || aPos.y > yMin)
                    && (map[newY][aPos.x] == 0 || map[newY][aPos.x] == 2)
                    && (enterNoGoZone || !willCreateLoop(Snake.DOWN, aPos, map, c))
                    ;
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveDownLoop check...", e);
            return false;
        }
    }

    private boolean canMoveLeft() {
        try {
            if (escapeFromBorder && (myHead.y == 0 || myHead.y == Y - 1)) {
                return false;
            } else {
                int newX = (myHead.x - 1 + X) % X;//myPos.x > 0 ? myPos.x - 1 : X-1;
                return  (mWrappedMode || myHead.x > xMin)
                        && myBody[myHead.y][newX] == 0
                        && snakeBodies[myHead.y][newX] == 0
                        && (!escapeFromHazard || hazardZone[myHead.y][newX] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[myHead.y][newX] == 0)
                        && (enterDangerZone || snakeNextMovePossibleLocations[myHead.y][newX] < myLen)
                        && (enterNoGoZone || !willCreateLoop(Snake.LEFT, myHead, null, 0))
                        ;
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveLeft check...", e);
            return false;
        }
    }

    private boolean canMoveLeft(Point aPos, int[][] map, int c) {
        try {
            int newX = (aPos.x - 1 + X) % X;//aPos.x > 0 ? aPos.x - 1 : X-1;
            return  (mWrappedMode || aPos.x > xMin)
                    && (map[aPos.y][newX] == 0 || map[aPos.y][newX] == 2)
                    && (enterNoGoZone || !willCreateLoop(Snake.LEFT, aPos, map, c))
                    ;
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveLeftLoop check...", e);
            return false;
        }
    }

    private void logState(final String method) {
        logState(method, LOG);
    }

    void logState(String msg, Logger LOG) {
        msg = msg
                + " Tn:" + turn
                + " st:" + getMoveIntAsString(state).substring(0, 2).toUpperCase() + "[" + state + "]"
                + " ph:" + tPhase
                + (escapeFromHazard ? " GETOUTHAZD" : "")
                + (mHazardPresent ? " goHazd? " + enterHazardZone : "")
                + (escapeFromBorder ? " GAWYBRD" : "")
                + " goBorder? " + enterBorderZone
                + " maxDeep? " + MAXDEEP
                + " goDanger? " + enterDangerZone
                + " goNoGo? " + enterNoGoZone
                + " "+gameId;
        LOG.info(msg);
    }

    String getMoveIntAsString(int move) {
        switch (move) {
            case Snake.UP:
                return Snake.U;
            case Snake.RIGHT:
                return Snake.R;
            case Snake.DOWN:
                return Snake.D;
            case Snake.LEFT:
                return Snake.L;
            default:
                return "UNKNOWN";
        }
    }

    void logBoard(Logger LOG) {

        StringBuffer z = new StringBuffer();
        z.append(" ┌");
        for(int i=0; i< X; i++){z.append('─');}
        z.append("┐");
        LOG.info(z.toString());

        for (int y = Y - 1; y >= 0; y--) {
            StringBuffer b = new StringBuffer();
            b.append(y % 10);
            b.append('│');
            for (int x = 0; x < X; x++) {
                if (myHead.x == x && myHead.y == y) {
                    b.append("X");
                } else if (myBody[y][x] == 1) {
                    b.append('c');
                } else if (snakeBodies[y][x] > 0) {
                    if (snakeBodies[y][x] == 1) {
                        b.append('+');
                    } else {
                        b.append('O');
                    }
                } else {
                    boolean isHazard = hazardZone[y][x] > 0;
                    boolean isFoodPlace = foodPlaces.contains(new Point(y, x));
                    if (snakeNextMovePossibleLocations[y][x] > 0) {
                        if (isFoodPlace) {
                            b.append('●');
                        } else {
                            b.append('◦');
                        }
                    } else if (isFoodPlace) {
                        if (isHazard) {
                            b.append('▓');
                        } else {
                            b.append('*');
                        }
                    } else if (isHazard) {
                        b.append('▒');
                    } else {
                        b.append(' ');
                    }
                }
            }
            b.append('│');
            LOG.info(b.toString());
        }

        StringBuffer y = new StringBuffer();
        y.append(" └");
        for(int i=0; i< X; i++){y.append('─');}
        y.append("┘");
        LOG.info(y.toString());

        StringBuffer b = new StringBuffer();
        b.append("  ");
        for (int i = 0; i < X; i++) {
            b.append(i % 10);
        }
        LOG.info(b.toString());
    }

    private void logMap(int[][] aMap, int c) {
        LOG.info("XXL TurnNo:"+turn+" MAXDEEP:"+MAXDEEP+" len:"+ myLen +" loopCount:"+c);
        StringBuffer z = new StringBuffer();
        z.append('┌');
        for(int i=0; i< X; i++){z.append('─');}
        z.append('┐');
        LOG.info(z.toString());

        for (int y = Y - 1; y >= 0; y--) {
            StringBuffer b = new StringBuffer();
            b.append('│');
            for (int x = 0; x < X; x++) {
                if(aMap[y][x]>0){
                    b.append('X');
                }else{
                    b.append(' ');
                }
            }
            b.append('│');
            LOG.info(b.toString());
        }
        StringBuffer y = new StringBuffer();
        y.append('└');
        for(int i=0; i< X; i++){y.append('─');}
        y.append('┘');
        LOG.info(y.toString());
    }

    /*ArrayList<Integer> cmdChain = null;
    int firstMoveToTry = -1;
    public String moveUp() {
        if (cmdChain.size() < 4 && cmdChain.contains(Snake.UP)) {
            // here we can generate randomness!
            return moveRight();
        } else {
            logState("UP");
            if (canMoveUp()) {
                LOG.debug("UP: YES");
                return Snake.U;
            } else {
                LOG.debug("UP: NO");
                // can't move...
                if (myPos.x < xMax / 2 || cmdChain.contains(Snake.LEFT)) {
                    state = Snake.RIGHT;
                    //LOG.debug("UP: NO - check RIGHT x:" + pos.x + " < Xmax/2:"+ xMax/2);
                    return moveRight();
                } else {
                    state = Snake.LEFT;
                    //LOG.debug("UP: NO - check LEFT");
                    return moveLeft();
                }
            }
        }
    }

    public String moveRight() {
        if (cmdChain.size() < 4 && cmdChain.contains(Snake.RIGHT)) {
            return moveDown();
        } else {
            logState("RI");
            if (canMoveRight()) {
                LOG.debug("RIGHT: YES");
                return Snake.R;
            } else {
                LOG.debug("RIGHT: NO");
                // can't move...
                if (myPos.x == xMax && tPhase > 0) {
                    if (myPos.y == yMax) {
                        // we should NEVER BE HERE!!
                        // we are in the UPPER/RIGHT Corner while in TraverseMode! (something failed before)
                        LOG.info("WE SHOULD NEVER BE HERE in T-PHASE >0");
                        tPhase = 0;
                        state = Snake.DOWN;
                        return moveDown();
                    } else {
                        state = Snake.LEFT;
                        return moveUp();
                    }
                } else {
                    return decideForUpOrDownUsedFromMoveLeftOrRight(Snake.RIGHT);
                }
            }
        }
    }

    public String moveDown() {
        if (cmdChain.size() < 4 && cmdChain.contains(Snake.DOWN)) {
            return moveLeft();
        } else {
            logState("DO");
            if (canMoveDown()) {
                LOG.debug("DOWN: YES");
                if (goForFood) {
                    return Snake.D;
                } else {
                    if (tPhase == 2 && myPos.y == yMin + 1) {
                        tPhase = 1;
                        state = Snake.RIGHT;
                        return moveRight();
                    } else {
                        return Snake.D;
                    }
                }
            } else {
                LOG.debug("DOWN: NO");
                // can't move...
                if (tPhase > 0) {
                    state = Snake.RIGHT;
                    return moveRight();
                } else {
                    if (myPos.x < xMax / 2 || cmdChain.contains(Snake.LEFT)) {
                        state = Snake.RIGHT;
                        return moveRight();
                    } else {
                        state = Snake.LEFT;
                        return moveLeft();
                    }
                }
            }
        }
    }
    public String moveLeft() {
        if (cmdChain.size() < 4 && cmdChain.contains(Snake.LEFT)) {
            return moveUp();
        } else {
            logState("LE");
            if (canMoveLeft()) {
                LOG.debug("LEFT: YES");
                if (goForFood) {
                    return Snake.L;
                } else {
                    // even if we "could" move to left - let's check, if we should/will follow our program...
                    if (myPos.x == xMin + 1) {
                        // We are at the left-hand "border" side of the board
                        if (tPhase != 2) {
                            tPhase = 1;
                        }
                        if (myPos.y == yMax) {
                            //LOG.debug("LEFT: STATE down -> RETURN: LEFT");
                            state = Snake.DOWN;
                            return Snake.L;
                        } else {
                            if (canMoveUp()) {
                                //LOG.debug("LEFT: STATE right -> RETURN: UP");
                                state = Snake.RIGHT;
                                return moveUp();
                            } else {
                                //LOG.debug("LEFT: RETURN: LEFT");
                                return Snake.L;
                            }
                        }
                    } else {
                        if ((yMax - myPos.y) % 2 == 1) {
                            // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                            // we simply really move to the LEFT (since we can!))
                            if (canMoveUp()) {
                                tPhase = 2;
                                return moveUp();
                            } else {
                                return Snake.L;
                            }
                        } else {
                            return Snake.L;
                        }
                    }
                }
            } else {
                // can't move...
                LOG.debug("LEFT: NO");
                // IF we can't go LEFT, then we should check, if we are at our special position
                // SEE also 'YES' part (only difference is, that we do not MOVE to LEFT here!)
                if (myPos.x == xMin + 1) {
                    // We are at the left-hand "border" side of the board
                    if (tPhase != 2) {
                        tPhase = 1;
                    }
                    if (myPos.y == yMax) {
                        state = Snake.DOWN;
                        //return Snake.L;
                        return moveDown();

                    } else {
                        if (canMoveUp()) {
                            state = Snake.RIGHT;
                            return moveUp();
                        } else {
                            //return Snake.L;
                            return moveDown();
                        }
                    }
                } else {
                    if ((yMax - myPos.y) % 2 == 1) {
                        // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                        // we simply really move to the LEFT (since we can!))
                        if (canMoveUp()) {
                            tPhase = 2;
                            return moveUp();
                        } else {
                            //return Snake.L;
                            return moveDown();
                        }
                    } else {
                        //return Snake.L;
                        // if we are in the pending mode, we prefer to go ALWAYS UP
                        return decideForUpOrDownUsedFromMoveLeftOrRight(Snake.LEFT);
                    }
                }
            }
        }
    }
    private String decideForUpOrDownUsedFromMoveLeftOrRight(int cmd) {
        // if we are in the pending mode, we prefer to go ALWAYS-UP
        if (tPhase > 0 && !cmdChain.contains(Snake.UP) && myPos.y < yMax) {
            state = Snake.UP;
            return moveUp();
        } else {
            if (myPos.y < yMax / 2 || cmdChain.contains(Snake.DOWN)) {
                state = Snake.UP;
                return moveUp();
            } else {
                state = Snake.DOWN;
                return moveDown();
            }
        }
    }*/

    String calculateNextMoveOptions() {
        int[] currentActiveBounds = new int[]{yMin, xMin, yMax, xMax};
        // checkSpecialMoves will also activate the 'goForFood' flag - so if this flag is set
        // we hat a primary and secondary direction in which we should move in order to find/get
        // food...

        // TODO: WARN - WE NEED TO ENABLE THIS AGAIN!!!
        List<Integer> killMoves = null;//checkSpecialMoves();

        SortedSet<Integer> options = new TreeSet<Integer>();
        // make sure that we check initially our preferred direction...
        if(foodGoForIt) {
            if (mFoodPrimaryDirection != -1) {
                options.add(mFoodPrimaryDirection);
            }
            if (mFoodSecondaryDirection != -1) {
                options.add(mFoodSecondaryDirection);
            }
        }
        options.add(state);
        options.add(Snake.UP);
        options.add(Snake.RIGHT);
        options.add(Snake.DOWN);
        options.add(Snake.LEFT);

        ArrayList<MoveWithState> possibleMoves = new ArrayList<>();
        Session.SavedState startState = saveState();
        for(int possibleDirection: options){
            restoreState(startState);
            restoreBoardBounds(currentActiveBounds);

            if(possibleDirection == mFoodPrimaryDirection || possibleDirection == mFoodSecondaryDirection){
                // So depending on the situation we want to take more risks in order to
                // get FOOD - but this Extra risk should be ONLY applied when making a
                // food move!
                if(foodFetchConditionGoHazard){
                    escapeFromHazard = false;
                    enterHazardZone = true;
                }
                if(foodFetchConditionGoBorder){
                    escapeFromBorder = false;
                    enterBorderZone = true;
                    setFullBoardBounds();
                }
            }
            // ok checking the next direction...
            String moveResult = moveDirection(possibleDirection, null);
            if(moveResult !=null && !moveResult.equals(Snake.REPEATLAST)) {
                MoveWithState move = new MoveWithState(moveResult, this);
                possibleMoves.add(move);
                LOG.info("EVALUATED WE can MOVE: " + move);
            }else{
                LOG.info("EVALUATED "+getMoveIntAsString(possibleDirection)+" FAILED");
            }
        }

        // once we got out of our loop we need to reset the active board bounds... so that min/max bounds
        // can be used in the getBestMove() code...
        restoreBoardBounds(currentActiveBounds);

        if(possibleMoves.size() == 0){
            doomed = true;
            // TODO Later - check, if any of the moves make still some sense?! [but I don't think so]
            LOG.error("***********************");
            LOG.error("DOOMED!");
            LOG.error("***********************");
            return Snake.REPEATLAST;
        }

        if(possibleMoves.size() == 1){
            return possibleMoves.get(0).move;
        }else{
            return getBestMove(possibleMoves, killMoves);
        }
    }

    private String getBestMove(ArrayList<MoveWithState> possibleMoves, List<Integer> killMoves) {
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

        if(mFoodPrimaryDirection != -1 && mFoodSecondaryDirection != -1){
            // TODO: decide for the better FOOD move...
            // checking if primary or secondary FOOD direction is possible
            // selecting the MOVE with less RISK (if there is one with)
            // avoid from border we can do so...
            MoveWithState pMove = moveKeyMap.get(mFoodPrimaryDirection);
            if (possibleMoves.contains(pMove)) {
                return pMove.move;
            }
            MoveWithState sMove = moveKeyMap.get(mFoodSecondaryDirection);
            if (possibleMoves.contains(sMove)) {
                return sMove.move;
            }
        } else if(mFoodPrimaryDirection != -1) {
            MoveWithState pMove = moveKeyMap.get(mFoodPrimaryDirection);
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
                case Snake.U:
                    resultingPos = getNewPointForDirection(myHead, Snake.UP);
                    break;
                case Snake.R:
                    resultingPos = getNewPointForDirection(myHead, Snake.RIGHT);
                    break;
                case Snake.D:
                    resultingPos = getNewPointForDirection(myHead, Snake.DOWN);
                    break;
                case Snake.L:
                    resultingPos = getNewPointForDirection(myHead, Snake.LEFT);
                    break;
            }

            if (resultingPos != null) {
                // checking if we are under direct threat
                int aMoveRisk = snakeNextMovePossibleLocations[resultingPos.y][resultingPos.x];
                if (aMoveRisk == 0 && !mWrappedMode) {

                    // TODO: not only the BORDER - also the MIN/MAX can be not so smart...

                    // ok no other snake is here in the area - but if we are a move to the BORDER
                    // then consider this move as a more risk move...
                    if (resultingPos.y == 0 || resultingPos.y == Y - 1) {
                        aMoveRisk++;
                    }
                    if (resultingPos.x == 0 || resultingPos.x == X - 1) {
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

        // for all the possible MOVE directions we might have to set our BoardBounds?!

        boolean canGoUp     = lowestRiskMoves.contains(Snake.U);
        boolean canGoRight  = lowestRiskMoves.contains(Snake.R);
        boolean canGoDown   = lowestRiskMoves.contains(Snake.D);
        boolean canGoLeft   = lowestRiskMoves.contains(Snake.L);

        switch (state){
            case Snake.UP:
                if(canGoUp) {
                    return Snake.U;
                } else {
                    if (myHead.x < xMax / 2 || !canGoLeft){ //cmdChain.contains(Snake.LEFT)) {
                        state = Snake.RIGHT;
                        if(canGoRight){
                            return Snake.R;
                        }
                    } else {
                        state = Snake.LEFT;
                        if(canGoLeft){
                            return Snake.L;
                        }
                    }
                }
                break;

            case Snake.RIGHT:
                if(canGoRight) {
                    return Snake.R;
                }else{
                    if (myHead.x == xMax && tPhase > 0) {
                        if (canGoDown && myHead.y == yMax) {
                            // we should NEVER BE HERE!!
                            // we are in the UPPER/RIGHT Corner while in TraverseMode! (something failed before)
                            LOG.info("WE SHOULD NEVER BE HERE in T-PHASE >0");
                            tPhase = 0;
                            state = Snake.DOWN;
                            return Snake.D;
                        } else {
                            state = Snake.LEFT;
                            //OLD CODE:
                            //return moveUp();
                            // NEW
                            if(canGoUp){
                                return Snake.U;
                            }
                        }
                    } else {
                        // NEW CODE... [when we are in the init phase - reached lower right corner
                        // we go to lower left corner]
                        if(myHead.y == yMin && tPhase == 0 && canGoLeft){
                            state = Snake.LEFT;
                            return Snake.L;
                        }else {
                            return decideForUpOrDownUsedFromMoveLeftOrRight(canGoUp, canGoDown);
                        }
                    }
                }
                break;

            case Snake.DOWN:
                if(canGoDown){
                    if (canGoRight && tPhase == 2 && myHead.y == yMin + 1) {
                        tPhase = 1;
                        state = Snake.RIGHT;
                        return Snake.R;
                    } else {
                        return Snake.D;
                    }
                } else{
                    if (canGoRight && tPhase > 0) {
                        state = Snake.RIGHT;
                        return Snake.R;
                    } else {
                        if (canGoRight && (myHead.x < xMax / 2 || !canGoLeft)) { //cmdChain.contains(Snake.LEFT)) {
                            state = Snake.RIGHT;
                            return Snake.R;
                        } else if(canGoLeft){
                            state = Snake.LEFT;
                            return Snake.L;
                        }else{
                            // looks lke we can't go LEFT or RIGHT...
                            // and we CAN NOT GO DOWN :-/
                        }
                    }
                }
                break;

            case Snake.LEFT:
                if(canGoLeft) {

                    // even if we "could" move to left - let's check, if we should/will follow our program...
                    if (myHead.x == xMin + 1) {
                        // We are at the left-hand "border" side of the board
                        if (tPhase != 2) {
                            tPhase = 1;
                        }
                        if (myHead.y == yMax) {
                            state = Snake.DOWN;
                            return Snake.L;
                        } else {
                            if (canGoUp) {
                                state = Snake.RIGHT;
                                return Snake.U;
                            } else {
                                return Snake.L;
                            }
                        }
                    } else if ((yMax - myHead.y) % 2 == 1) {
                        // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                        // we simply really move to the LEFT (since we can!))
                        if (canGoUp) {
                            tPhase = 2;
                            return Snake.U;
                        } else {
                            return Snake.L;
                        }
                    } else {
                        return Snake.L;
                    }

                } else {

                    // IF we can't go LEFT, then we should check, if we are at our special position
                    // SEE also 'YES' part (only difference is, that we do not MOVE to LEFT here!)
                    if (myHead.x == xMin + 1) {
                        // We are at the left-hand "border" side of the board
                        if (tPhase != 2) {
                            tPhase = 1;
                        }
                        if (myHead.y == yMax) {
                            state = Snake.DOWN;
                            //return Snake.L;
                            //OLD CODE:
                            //return moveDown();
                            // NEW
                            if (canGoDown) {
                                return Snake.D;
                            }else{
                                // TODO ?!
                            }
                        } else {
                            if (canGoRight) {
                                state = Snake.RIGHT;
                                return Snake.R;
                            } else if (canGoUp) {
                                state = Snake.RIGHT;
                                return Snake.U;
                            }
                        }
                    }else if(myHead.x == xMax){
                        if (canGoLeft){
                            state = Snake.LEFT;
                            return Snake.L;
                        }else if (canGoUp) {
                            state = Snake.LEFT;
                            return Snake.U;
                        }

                    } else {
                        if ((yMax - myHead.y) % 2 == 1) {
                            // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                            // we simply really move to the LEFT (since we can!))
                            if (canGoUp) {
                                tPhase = 2;
                                return Snake.U;
                            } else {
                                //return Snake.L;
                                //OLD CODE:
                                //return moveDown();

                                // NEW
                                if(canGoDown){
                                    return Snake.D;
                                }
                            }
                        } else {
                            // return Snake.L;
                            // if we are in the pending mode, we prefer to go ALWAYS UP
                            return decideForUpOrDownUsedFromMoveLeftOrRight(canGoUp, canGoDown);
                        }
                    }
                }
                break;
        }

        return finalMove;
    }

    private String decideForUpOrDownUsedFromMoveLeftOrRight(boolean canGoUp, boolean canGoDown) {
        // if we are in the pending mode, we prefer to go ALWAYS-UP
        if (tPhase > 0 && canGoUp && myHead.y < yMax) {
            state = Snake.UP;
            return Snake.U;
        } else {
            if (myHead.y < yMax / 2 || ! canGoDown) {
                state = Snake.UP;
                return Snake.U;
            } else {
                state = Snake.DOWN;
                return Snake.D;
            }
        }
    }
}