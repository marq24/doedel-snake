package com.emb.bs.ite;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Session {

    static final int UNKNOWN = -1;
    static final int UP = 0;
    static final int RIGHT = 1;
    static final int DOWN = 2;
    static final int LEFT = 3;
    static final int DOOMED = 99;

    static String getMoveIntAsString(int move) {
        switch (move) {
            case UP:
                return Snake.U;
            case RIGHT:
                return Snake.R;
            case DOWN:
                return Snake.D;
            case LEFT:
                return Snake.L;
            case DOOMED:
                return "DOOMED";
            default:
                return "UNKNOWN";
        }
    }

    private static final HashMap<Integer, MoveWithState> intMovesToMoveKeysMap = new HashMap<>();
    static{
        intMovesToMoveKeysMap.put(UP, new MoveWithState(UP));
        intMovesToMoveKeysMap.put(RIGHT, new MoveWithState(RIGHT));
        intMovesToMoveKeysMap.put(DOWN, new MoveWithState(DOWN));
        intMovesToMoveKeysMap.put(LEFT, new MoveWithState(LEFT));
    }

    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    // just for logging...
    ArrayList<String> players;
    String gameId;
    int turn;

    // stateful stuff
    private int tPhase = 0;
    private int state = UP;
    private int mFoodPrimaryDirection = -1;
    private int mFoodSecondaryDirection = -1;
    Point lastTurnTail = null;

    // Food direction stuff
    private Point foodActive = null;
    private boolean foodGoForIt = false;
    private boolean foodFetchConditionGoHazard = false;
    private boolean foodFetchConditionGoBorder = false;

    String LASTMOVE = null;

    Point myHead;
    Point myNeck;
    Point myTail;
    int myLen;
    int myHealth;

    int X = -1;
    int Y = -1;
    private int xMin, yMin, xMax, yMax;

    private boolean doomed = false;
    ArrayList<Point> snakeHeads = null;
    int[][] snakeBodies = null;
    int[][] snakeThisMovePossibleLocations = null;
    HashMap<Point, ArrayList<Integer>> snakeThisMovePossibleLocationList = null;
    int maxOtherSnakeLen = 0;
    int[][] myBody = null;
    int[][] hazardZone = null;
    ArrayList<Point> foodPlaces = null;

    private int MAXDEEP = 0;
    private boolean ignoreOtherTargets = false;
    private boolean enterHazardZone = false;
    private boolean enterBorderZone = false;
    private boolean enterDangerZone = false;
    private boolean enterNoGoZone = false;

    private boolean escapeFromBorder = false;
    private boolean escapeFromHazard = false;


    private boolean mHungerMode = true;

    boolean mWrappedMode = false;
    private boolean mSoloMode = false;
    private boolean mRoyaleMode = false;
    private boolean mConstrictorMode = false;
    private boolean mHazardPresent = false;

    class SavedState {
        int sdState = state;
        int sTPhase = tPhase;
        boolean sEscapeFromBorder = escapeFromBorder;
        boolean sEscapeFromHazard = escapeFromHazard;
        boolean sIgnoreOtherTargets = ignoreOtherTargets;
        boolean sEnterHazardZone = enterHazardZone;
        boolean sEnterBorderZone = enterBorderZone;
        boolean sEnterDangerZone = enterDangerZone;
        boolean sEnterNoGoZone = enterNoGoZone;
        int sMAXDEEP = MAXDEEP;

        @Override
        public String toString() {
                return
                      " st:" + getMoveIntAsString(sdState).substring(0, 2).toUpperCase() + "[" + sdState + "]"
                    + " ph:" + sTPhase
                    + (sEscapeFromHazard ? " GETOUTHAZD" : "")
                    + (mHazardPresent ? " goHazd? " + sEnterHazardZone : "")
                    + " goBorder? " + sEnterBorderZone
                    + (sEscapeFromBorder ? " GAWYBRD" : "")
                    + (sIgnoreOtherTargets ? " IGNOREOTHERS" : "")
                    + " maxDeep? " + sMAXDEEP
                    + " goDanger? " + sEnterDangerZone
                    + " goNoGo? " + sEnterNoGoZone;
        }
    }

    SavedState saveState() {
        return new SavedState();
    }

    void restoreState(SavedState savedState) {
        state = savedState.sdState;
        tPhase = savedState.sTPhase;

        escapeFromBorder = savedState.sEscapeFromBorder;
        escapeFromHazard = savedState.sEscapeFromHazard;
        ignoreOtherTargets = savedState.sIgnoreOtherTargets;
        enterHazardZone = savedState.sEnterHazardZone;
        enterBorderZone = savedState.sEnterBorderZone;
        MAXDEEP = savedState.sMAXDEEP;
        enterDangerZone = savedState.sEnterDangerZone;
        enterNoGoZone = savedState.sEnterNoGoZone;
    }

    class PointWithBool{
        Point point;
        boolean bool;

        public PointWithBool(Point point, boolean bool) {
            this.point = point;
            this.bool = bool;
        }
    }

    static class PointWithInt{
        Point point;
        int val;

        public PointWithInt(Point point, int val) {
            this.point = point;
            this.val = val;
        }
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
        ignoreOtherTargets = false;
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
        snakeThisMovePossibleLocations = new int[Y][X];
        snakeThisMovePossibleLocationList = new HashMap<>();
        maxOtherSnakeLen = 0;

        myBody = new int[Y][X];

        // TODO: MAXDEEP can be myLen-1 IF THERE IS NO FODD in front of us
        // really???
        MAXDEEP = Math.max(myLen, Y*X/2);//Math.min(len, 20);

        foodGoForIt = false;
        foodFetchConditionGoHazard = false;
        foodFetchConditionGoBorder = false;

        foodPlaces = new ArrayList<>();

        hazardZone = new int[Y][X];

        escapeFromBorder = false;
        escapeFromHazard = false;

        if(gameType != null) {
            switch (gameType) {
                case "standard":
                case "squad":
                    break;

                case "solo":
                    mHungerMode = false;
                    mSoloMode = true;
                    //enterBorderZone = true;
                    //setFullBoardBounds();
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
                    mConstrictorMode = true;
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

        if (    myHead.y == 0 ||
                myHead.y == Y - 1 ||
                myHead.x == 0 ||
                myHead.x == X - 1
        ) {
            escapeFromBorder = true;
        }

        mHazardPresent = hazardDataIsPresent;
        if(mHazardPresent) {
            if (hazardZone[myHead.y][myHead.x] > 0 && myHealth < 95) {
                escapeFromHazard = true;
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
            escapeFromHazard = false;
        }
    }

    private void multiplyHazardThreadsInMap(){
        // POPULATE HAZARD DAMAGE...
        for(int k=0; k < (Math.max(X, Y) / 2) + 1; k++) {
            for (int y = 0; y < Y; y++) {
                for (int x = 0; x < X; x++) {
                    if (hazardZone[y][x] > k) {
                        try {
                            boolean b0 = hazardZone[y + 1][x - 1] > k;
                            boolean b1 = hazardZone[y + 0][x - 1] > k;
                            boolean b2 = hazardZone[y - 1][x - 1] > k;

                            boolean b3 = hazardZone[y + 1][x + 0] > k;
                            boolean b4 = hazardZone[y - 1][x + 0] > k;

                            boolean b5 = hazardZone[y + 1][x + 1] > k;
                            boolean b6 = hazardZone[y + 0][x + 1] > k;
                            boolean b7 = hazardZone[y - 1][x + 1] > k;
                            if (b0 && b1 && b2 && b3 && b4 && b5 && b6 && b7) {
                                hazardZone[y][x]++;
                            }
                        } catch (IndexOutOfBoundsException e) {
                        }
                    }
                }
            }
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
            } else if(MAXDEEP > 1) {
                LOG.debug("activate MAXDEEP TO: " + MAXDEEP);
                if(MAXDEEP <= Math.max(myLen/1.2, 2) && !ignoreOtherTargets){
                    ignoreOtherTargets = true;
                    LOG.debug("activate IGNOREOTHERS (TARGETS) and reset MAXDEEP from:" + MAXDEEP+" to:"+myLen);
                    MAXDEEP = Math.max(myLen, Y*X/2);
                } else {
                    MAXDEEP--;
                }
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

    private int moveDirection(int move, RiskState risk) {
        // checkForOWN Neck...
        Point resPos = getNewPointForDirection(myHead, move);
        if(resPos.equals(myNeck)){
            return UNKNOWN;
        }else {
            if(risk == null){
                risk = new RiskState();
            }else{
                risk.next();
            }
            if (risk.endReached) {
                return DOOMED;
            } else if (risk.retry) {
                //logState(moveAsString);
                boolean canMove = false;

                switch (move){
                    case UP:
                        canMove = canMoveUp();
                        break;
                    case RIGHT:
                        canMove = canMoveRight();
                        break;
                    case DOWN:
                        canMove = canMoveDown();
                        break;
                    case LEFT:
                        canMove = canMoveLeft();
                        break;
                }
                if (canMove) {
                    LOG.debug(getMoveIntAsString(move)+": YES");
                    return move;
                }else{
                    LOG.debug(getMoveIntAsString(move)+": NO");
                    return moveDirection(move, risk);
                }
            }
            return UNKNOWN;
        }
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

    private void checkSpecialMoves() {
        if (myHealth < 41 || (!mSoloMode && (myLen - getAdvantage() <= maxOtherSnakeLen))) {
            LOG.info("Check for FOOD! health:" + myHealth + " len:" + myLen +"(-"+getAdvantage()+")"+ "<=" + maxOtherSnakeLen);
            // ok we need to start to fetch FOOD!
            // we should move into the direction of the next FOOD! (setting our preferred direction)
            checkFoodMoves();
        }else{
            // need to reset all food parameters...
            resetFoodStatus();
        }
    }

    private void checkFoodMoves() {
        Point closestFood = null;

        // we remove all food's that are in direct area of other snakes heads
        // I don't want to battle for food with others (now)
        ArrayList<Point> availableFoods = new ArrayList<>(foodPlaces.size());
        availableFoods.addAll(foodPlaces);

        if (myHealth > 25) {
            // in wrappedMode there are no corners... and in the first 5 turns we might pick
            // up food that is around us...
            if(turn > 20 && !mWrappedMode && !mSoloMode) {
                // food in CORNERS is TOXIC (but if we are already IN the corner we will
                // take it!
                //if (!(myHead.x == 0 && myHead.y <= 1) || (myHead.x <= 1 && myHead.y == 0)) {
                    availableFoods.remove(new Point(0, 0));
                //}
                //if (!(myHead.x == X - 1 && myHead.y <= 1) || (myHead.x <= X - 2 && myHead.y == 0)) {
                    availableFoods.remove(new Point(0, X - 1));
                //}
                //if (!(myHead.x == 0 && myHead.y <= Y - 2) || (myHead.x <= 1 && myHead.y == Y - 1)) {
                    availableFoods.remove(new Point(Y - 1, 0));
                //}
                //if (!(myHead.x == X - 1 && myHead.y >= Y - 2) || (myHead.x >= X - 2 && myHead.y == Y - 1)) {
                    availableFoods.remove(new Point(Y - 1, X - 1));
                //}
            }
            for (Point h : snakeHeads) {
                // food that is head of another snake that is longer or has
                // the same length should be ignored...
                if (snakeBodies[h.y][h.x] >= myLen){
                    availableFoods.remove(new Point(h.y + 1, h.x + 0));
                    availableFoods.remove(new Point(h.y + 0, h.x + 1));
                    availableFoods.remove(new Point(h.y - 1, h.x + 0));
                    availableFoods.remove(new Point(h.y + 0, h.x - 1));
                }
            }
        }

if(turn >= Snake.debugTurn){
    LOG.debug("HALT");
}

        TreeMap<Integer, ArrayList<Point>> foodTargetsByDistance = new TreeMap<>();
        for (Point f : availableFoods) {
            int dist = getPointDistance(f, myHead);
            if(!isFoodLocatedAtBorder(f) || dist == 1 || (dist <= 3 && myHealth < 61) || myHealth < 31) {
                boolean addFoodAsTarget = true;
                for (Point h : snakeHeads) {
                    int otherSnakesDist = getPointDistance(f, h);
                    boolean otherIsStronger = snakeBodies[h.y][h.x] > myLen;
                    if(dist < ((X+Y)/2) && (dist > otherSnakesDist || (dist == otherSnakesDist && otherIsStronger))) {
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

if(turn >= Snake.debugTurn){
    LOG.debug("HALT");
}

            int yDelta = myHead.y - closestFood.y;
            int xDelta = myHead.x - closestFood.x;
            int preferredYDirection = -1;
            int preferredXDirection = -1;
            if (mFoodPrimaryDirection == -1 || yDelta == 0 || xDelta == 0) {
                if(mWrappedMode && Math.abs(yDelta) > Y/2) {
                    if(myHead.y < Y/2){
                        preferredYDirection = DOWN;
                    }else {
                        preferredYDirection = UP;
                    }
                } else if (yDelta > 0) {
                    preferredYDirection = DOWN;
                } else if (yDelta < 0) {
                    preferredYDirection = UP;
                }

                if((mWrappedMode && Math.abs(xDelta) > X/2)){
                    if(myHead.x < X/2) {
                        preferredXDirection = LEFT;
                    }else{
                        preferredXDirection = RIGHT;
                    }
                }else if (xDelta > 0) {
                    preferredXDirection = LEFT;
                } else if (xDelta < 0){
                    preferredXDirection = RIGHT;
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
                //if(goFullBorder || turn < 30 || myLen < 15 || myLen - 1 < maxOtherSnakeLen || isLocatedAtBorder(closestFood)){
                if(goFullBorder || turn < 30 || isFoodLocatedAtBorder(closestFood)){
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
            LOG.info("NO NEARBY FOOD FOUND "+foodTargetsByDistance+" ["+foodPlaces+"]");
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

    private int getPointDistance(Point p1, Point p2){
        if(!mWrappedMode){
            return Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y);
        }else{
            // in wrappedMode: if p1.x = 0 & p2.x = 11, then distance is 1
            return  Math.min(Math.abs(p1.x - (p2.x + X)), Math.min(Math.abs((p1.x + X) - p2.x), Math.abs(p1.x - p2.x))) +
                    Math.min(Math.abs(p1.y - (p2.y + Y)), Math.min(Math.abs((p1.y + Y) - p2.y), Math.abs(p1.y - p2.y)));
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

    private boolean isFoodLocatedAtBorder(Point p) {
        if(turn < 21 || mWrappedMode){
            return  false;//hazardNearbyPlaces.contains(p);
        }else {
            if(turn < 50 || myLen < 15 || (!mHazardPresent && myLen - 1 < maxOtherSnakeLen) || myLen < maxOtherSnakeLen - 5){
                return  p.y == 0
                        || p.y == Y - 1
                        || p.x == 0
                        || p.x == X - 1
                        ;
            }else {
                // 2022/03/09 - replaced the <= and >= with the
                // < and > since FOOD near the border should be ok?!
                return  p.y < yMin
                        || p.y > yMax
                        || p.x < xMin
                        || p.x > xMax
                        ;
            }
        }
    }

    private boolean isPosLocatedAtBorder(Point p) {
        if(turn < 21 || mWrappedMode){
            return  false;
        }else {
            if(turn < 50){
                return  p.y == 0
                        || p.y == Y - 1
                        || p.x == 0
                        || p.x == X - 1
                        ;
            }else {
                return  p.y < yMin
                        || p.y > yMax
                        || p.x < xMin
                        || p.x > xMax
                        ;
            }
        }
    }

    private boolean isHazardFreeMove(MoveWithState aMove){
        Point pos = aMove.getResPosForMyHead(this);
        return !mHazardPresent || hazardZone[pos.y][pos.x] == 0;
    }

    Point getNewPointForDirection(Point aPos, int move){
        Point newPos = aPos.clone();
        if(mWrappedMode) {
            switch (move) {
                case UP:
                    newPos.y = (newPos.y + 1) % Y;
                    break;
                case RIGHT:
                    newPos.x = (newPos.x + 1) % X;
                    break;
                case DOWN:
                    newPos.y = (newPos.y - 1 + Y) % Y;
                    break;
                case LEFT:
                    newPos.x = (newPos.x -1 + X) % X;
                    break;
            }
        }else{
            switch (move) {
                case UP:
                    newPos.y++;
                    break;
                case RIGHT:
                    newPos.x++;
                    break;
                case DOWN:
                    newPos.y--;
                    break;
                case LEFT:
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
if(turn >= Snake.debugTurn){
    LOG.debug("HALT");
}
        try {
            count++;
            if(count <= MAXDEEP) {
                Point newPos = getNewPointForDirection(aPos, move);
                if(lastTurnTail != null && newPos.equals(lastTurnTail) && !foodPlaces.contains(newPos)){
                    return false;
                }

                // so in the finalMap we have the picture of the MOVE RESULT
                if(finalMap == null) {
                    finalMap = new int[Y][X];
                    finalMap[myHead.y][myHead.x] = 1;
                    for (int y = 0; y < Y; y++) {
                        for (int x = 0; x < X; x++) {
                            if(lastTurnTail != null && lastTurnTail.y == y && lastTurnTail.x == x) {
                                finalMap[y][x] = 2; // the GOLDEN ASS
                            }else if (myBody[y][x] > 0) {
                                finalMap[y][x] = 1;
                            } else if (snakeBodies[y][x] > 0) {
                                finalMap[y][x] = 1;
                            } else if (!ignoreOtherTargets && snakeThisMovePossibleLocations[y][x] > 0) {
                                finalMap[y][x] = 1;

                                /*int otherSnakeLen = snakeNextMovePossibleLocations[y][x];
                                // finalMap is ONLY null, when this is called directly with the "myHead" pos
                                // and the "move" direction - so the 'newPos' is actually the next location
                                // of our snake after this "planed" move... if this location is actually the
                                // possibleTargetLocation of another sneak, we can/should check, if we are
                                // stringer => if we are stronger we can consider this field as FREE...
                                if(!(newPos.y == y && newPos.x == x && myLen > otherSnakeLen)){
                                    finalMap[y][x] = 1;
                                }*/
                                // BUT all this is OBSOLETE, since we will anyhow mark the next position of
                                // our sneak as "visited" in the finalMap...
                            }
                        }
                    }
                }
                finalMap[newPos.y][newPos.x] = 1;

                boolean noUP = !canMoveUp(newPos, finalMap, count);
                boolean noDW = !canMoveDown(newPos, finalMap, count);
                boolean noLF = !canMoveLeft(newPos, finalMap, count);
                boolean noRT = !canMoveRight(newPos, finalMap, count);

if(turn >= Snake.debugTurn){
    //logMap(finalMap, count);
    LOG.debug("HALT");
}
                if (noUP && noDW && noLF && noRT) {
                    return true;
                }
            }

        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ willCreateLoop " + getMoveIntAsString(move) + " check...", e);
        }
        return false;
    }

    private boolean XwillCreateLoop(int move, Point aPos, int[][] finalMap, int count) {
        Point newPos = getNewPointForDirection(aPos, move);

        Map<Integer, ArrayList<Point>> data01 = XgroupSpaces(newPos);
        //ignoreOtherTargets = true;
        //Map<Integer, ArrayList<Point>> data02 = XgroupSpaces(newPos);
        //ignoreOtherTargets = false;

        if(data01.containsKey(Integer.MAX_VALUE)){
            ArrayList<Point> pointsFromWhichTheTailCanBeCaught = data01.get(Integer.MAX_VALUE);
        }

        HashMap<Point, Integer> space = new HashMap<>();
        for(ArrayList<Point> aList: data01.values()){
            int len = aList.size();
            for(Point aPoint: aList){
                space.put(aPoint, len);
            }
        }

        if(move == LEFT){
            if(Snake.debugTurn == turn){
                LOG.info("HALT");//+data01+" "+data02);
            }
        }

        return false;
    }

    private Map<Integer, ArrayList<Point>> XgroupSpaces(Point aPos){

        // generating the resulting MAP (occupied fields after the selected
        // move will be made)...
        int[][] finalMap = new int[Y][X];
        finalMap[myHead.y][myHead.x] = 1;
        for (int y = 0; y < Y; y++) {
            for (int x = 0; x < X; x++) {
                if(lastTurnTail != null && lastTurnTail.y == y && lastTurnTail.x == x) {
                    finalMap[y][x] = 2; // the GOLDEN ASS
                }else if (myBody[y][x] > 0) {
                    finalMap[y][x] = 1;
                } else if (snakeBodies[y][x] > 0) {
                    finalMap[y][x] = 1;
                } else if (!ignoreOtherTargets && snakeThisMovePossibleLocations[y][x] > 0) {
                    finalMap[y][x] = 1;
                }
            }
        }
        finalMap[aPos.y][aPos.x] = 1;

        //the available fields map...
        int num = 1;
        int[][] freeMap = new int[Y][X];
        for (int y = yMin; y <= yMax; y++) {
            for (int x = yMin; x <= xMax; x++) {
                if(finalMap[y][x] == 0) {
                    freeMap[y][x] = num++;
                } else if (finalMap[y][x] == 2){
                    freeMap[y][x] = Integer.MAX_VALUE;
                }
            }
        }
        // the map where each field that is not occupied received a number...
        //logFreeMap2(freeMap, aPos, 0);

        int maxLoops = Math.max(X,Y) / 2 + 1;
        for(int i=0; i < maxLoops; i++) {
            for (int y = 0; y < Y; y++) {
                for (int x = 0; x < X; x++) {
                    int count = 0;
                    if (Xup(freeMap, y, x)) {
                        count++;
                    }
                    if (Xdown(freeMap, y, x)) {
                        count++;
                    }
                    if (Xleft(freeMap, y, x)) {
                        count++;
                    }
                    if (Xright(freeMap, y, x)) {
                        count++;
                    }

                    if (count < 2) {
                        freeMap[y][x] = 0;
                    }
                }
            }
        }
        // removed from the map the fields that have only a single connection [aka DEAD ends]
        //logFreeMap2(freeMap, aPos, 0);

        for (int i=0; i < maxLoops; i++){
            for (int y = 0; y < Y; y++) {
                for (int x = 0; x < X; x++) {
                    Xwash(freeMap, y, x);
                }
            }
            for (int y = Y - 1; y >= 0; y--) {
                for (int x = X - 1; x >= 0; x--) {
                    Xwash(freeMap, y, x);
                }
            }
        }
        // populate the MAX field value to all areas...
        logFreeMap2(freeMap, aPos, 0);

        // finally, grouping/counting the size of the "free" space fields
        HashMap<Integer, ArrayList<Point>> data = new HashMap<>();
        for (int y = 0; y < Y; y++) {
            for (int x = 0; x < X; x++) {
                int key = freeMap[y][x];
                ArrayList<Point> list = data.get(key);
                if(list == null){
                    list = new ArrayList<>();
                    data.put(key, list);
                }
                list.add(new Point(y,x));
            }
        }
        return data;
    }

    private boolean Xup(int[][] map, int y, int x){
        return y + 1 < Y && map[y + 1][x] > 0;
    }
    private boolean Xdown(int[][] map, int y, int x){
        return y - 1 >= 0 && map[y - 1][x] > 0;
    }
    private boolean Xleft(int[][] map, int y, int x){
        return x - 1 >= 0 && map[y][x - 1] > 0;
    }
    private boolean Xright(int[][] map, int y, int x){
        return x + 1 < X && map[y][x + 1] > 0;
    }

    private void Xwash(int[][] map, int y, int x) {
        int i0 = map[y][x];
        if (i0 > 0) {
            int maxValue = i0;

            boolean up = Xup(map, y, x);
            boolean down = Xdown(map, y, x);
            boolean right = Xright(map, y, x);
            boolean left = Xleft(map, y, x);

            // find the max value (of all four neighbours)
            if (up) {
                maxValue = Math.max(maxValue, map[y + 1][x]);
            }
            if (down) {
                maxValue = Math.max(maxValue, map[y - 1][x]);
            }
            if (right) {
                maxValue = Math.max(maxValue, map[y][x + 1]);
            }
            if (left) {
                maxValue = Math.max(maxValue, map[y][x - 1]);
            }

            // populate the max value...
            if(up) {
                map[y + 1][x] = maxValue;
            }
            if(down){
                map[y - 1][x] = maxValue;
            }
            if(right){
                map[y][x + 1] = maxValue;
            }
            if(left){
                map[y][x - 1] = maxValue;
            }
            map[y][x] = maxValue;
        }
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
                        && (enterBorderZone || !(newY == 0 || newY == Y-1 || myHead.x == 0 || myHead.x == X-1))
                        && (!escapeFromHazard || hazardZone[newY][myHead.x] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[newY][myHead.x] == 0)
                        && (enterDangerZone || snakeThisMovePossibleLocations[newY][myHead.x] < myLen)
                        && (enterNoGoZone || !willCreateLoop(UP, myHead, null,0));
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
                    && (enterNoGoZone || !willCreateLoop(UP, aPos, map, c))
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
                        && (enterBorderZone || !(myHead.y == 0 || myHead.y == Y-1 || newX == 0 || newX == X-1))
                        && (!escapeFromHazard || hazardZone[myHead.y][newX] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[myHead.y][newX] == 0)
                        && (enterDangerZone || snakeThisMovePossibleLocations[myHead.y][newX] < myLen)
                        && (enterNoGoZone || !willCreateLoop(RIGHT, myHead, null, 0))
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
                    && (enterNoGoZone || !willCreateLoop(RIGHT, aPos, map, c))
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
                int newY = (myHead.y - 1 + Y) % Y;//myPos.y > 0 ? myPos.y - 1 : Y - 1;
                return  (mWrappedMode || myHead.y > yMin)
                        && myBody[newY][myHead.x] == 0
                        && snakeBodies[newY][myHead.x] == 0
                        && (enterBorderZone || !(newY == 0 || newY == Y-1 || myHead.x == 0 || myHead.x == X-1))
                        && (!escapeFromHazard || hazardZone[newY][myHead.x] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[newY][myHead.x] == 0)
                        && (enterDangerZone || snakeThisMovePossibleLocations[newY][myHead.x] < myLen)
                        && (enterNoGoZone || !willCreateLoop(DOWN, myHead, null, 0))
                        ;
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveDown check...", e);
            return false;
        }
    }

    private boolean canMoveDown(Point aPos, int[][] map, int c) {
        try {
            int newY = (aPos.y - 1 + Y) % Y; // aPos.y > 0 ? aPos.y - 1 : Y - 1;
            return  (mWrappedMode || aPos.y > yMin)
                    && (map[newY][aPos.x] == 0 || map[newY][aPos.x] == 2)
                    && (enterNoGoZone || !willCreateLoop(DOWN, aPos, map, c))
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
                        && (enterBorderZone || !(myHead.y == 0 || myHead.y == Y-1 || newX == 0 || newX == X-1))
                        && (!escapeFromHazard || hazardZone[myHead.y][newX] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[myHead.y][newX] == 0)
                        && (enterDangerZone || snakeThisMovePossibleLocations[myHead.y][newX] < myLen)
                        && (enterNoGoZone || !willCreateLoop(LEFT, myHead, null, 0))
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
                    && (enterNoGoZone || !willCreateLoop(LEFT, aPos, map, c))
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
                + " goBorder? " + enterBorderZone
                + (escapeFromBorder ? " GAWYBRD" : "")
                + (ignoreOtherTargets ? " IGNOREOTHERS" : "")
                + " maxDeep? " + MAXDEEP
                + " goDanger? " + enterDangerZone
                + " goNoGo? " + enterNoGoZone
                + " "+gameId;
        LOG.info(msg);
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
                } else if(lastTurnTail !=null && lastTurnTail.x == x && lastTurnTail.y == y){
                    b.append('y');
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
                    if (snakeThisMovePossibleLocations[y][x] > 0) {
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
        z.append(" ┌");
        for(int i=0; i< X; i++){z.append('─');}
        z.append('┐');
        LOG.info(z.toString());

        for (int y = Y - 1; y >= 0; y--) {
            StringBuffer b = new StringBuffer();
            b.append(y % 10);
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
        y.append(" └");
        for(int i=0; i< X; i++){y.append('─');}
        y.append('┘');
        LOG.info(y.toString());
        StringBuffer b = new StringBuffer();
        b.append("  ");
        for (int i = 0; i < X; i++) {
            b.append(i % 10);
        }
        LOG.info(b.toString());
    }

    private void logFreeMap(int[][] aMap, int c) {
        LOG.info("XXL TurnNo:"+turn+" len:"+ myLen +" maxSpace:"+c);
        StringBuffer z = new StringBuffer();
        z.append(" ┌");
        for(int i=0; i< X; i++){z.append('─');}
        z.append('┐');
        LOG.info(z.toString());

        for (int y = Y - 1; y >= 0; y--) {
            StringBuffer b = new StringBuffer();
            b.append(y % 10);
            b.append('│');
            for (int x = 0; x < X; x++) {
                if(aMap[y][x]>0){
                    b.append(aMap[y][x]%10);
                }else{
                    b.append(' ');
                }
            }
            b.append('│');
            LOG.info(b.toString());
        }
        StringBuffer y = new StringBuffer();
        y.append(" └");
        for(int i=0; i< X; i++){y.append('─');}
        y.append('┘');
        LOG.info(y.toString());
        StringBuffer b = new StringBuffer();
        b.append("  ");
        for (int i = 0; i < X; i++) {
            b.append(i % 10);
        }
        LOG.info(b.toString());
    }

    private void logFreeMap2(int[][] aMap, Point pos, int c) {
        LOG.info("XXL TurnNo:"+turn+" len:"+ myLen +" maxSpace:"+c);
        StringBuffer z = new StringBuffer();
        z.append(" ┌");
        for(int i=0; i< X; i++){z.append("───");}
        z.append('┐');
        LOG.info(z.toString());

        for (int y = Y - 1; y >= 0; y--) {
            StringBuffer b = new StringBuffer();
            b.append(y % 10);
            b.append('│');
            for (int x = 0; x < X; x++) {
                if(myHead.y == y && myHead.x == x) {
                    b.append("| X");
                } else if(pos != null && pos.y == y && pos.x == x) {
                    b.append("| *");
                }else if(aMap[y][x]>0){
                    if(aMap[y][x] == Integer.MAX_VALUE) {
                        b.append("|yy");
                    }else{
                        int val = aMap[y][x]%100;
                        if(val < 10){
                            b.append("|0" + val);
                        }else {
                            b.append("|" + val);
                        }
                    }
                }else{
                    b.append("|  ");
                }
            }
            b.append('│');
            LOG.info(b.toString());
        }
        StringBuffer y = new StringBuffer();
        y.append(" └");
        for(int i=0; i< X; i++){y.append("───");}
        y.append('┘');
        LOG.info(y.toString());
        StringBuffer b = new StringBuffer();
        b.append("  ");
        for (int i = 0; i < X; i++) {
            if(i<10){
                b.append("|0" + i);
            }else {
                b.append("|" + i);
            }
        }
        LOG.info(b.toString());
    }

    private static int[] options = new int[]{UP, RIGHT, DOWN, LEFT};
    MoveWithState calculateNextMoveOptions() {
        int[] currentActiveBounds = new int[]{yMin, xMin, yMax, xMax};
        // checkSpecialMoves will also activate the 'goForFood' flag - so if this flag is set
        // we hat a primary and secondary direction in which we should move in order to find/get
        // food...

        checkSpecialMoves();

        ArrayList<MoveWithState> possibleMoves = new ArrayList<>();
        Session.SavedState startState = saveState();

        for(int possibleDirection: options){
            restoreState(startState);
            restoreBoardBounds(currentActiveBounds);

if(turn >= Snake.debugTurn){
    LOG.debug("Checking... "+getMoveIntAsString(possibleDirection));
    LOG.debug("HALT");
}
            if(possibleDirection == mFoodPrimaryDirection || possibleDirection == mFoodSecondaryDirection){
                // So depending on the situation we want to take more risks in order to
                // get FOOD - but this Extra risk should be ONLY applied when making a
                // food move!
                if(foodFetchConditionGoHazard){
                    LOG.info("FOOD caused ENTER-Hazard TRUE");
                    escapeFromHazard = false;
                    enterHazardZone = true;
                }
                if(foodFetchConditionGoBorder){
                    LOG.info("FOOD caused ENTER-Border TRUE");
                    escapeFromBorder = false;
                    enterBorderZone = true;
                    setFullBoardBounds();
                }
            }

if(turn >= Snake.debugTurn){
    LOG.debug("HALT");
}
            // ok checking the next direction...
            int moveResult = moveDirection(possibleDirection, null);
            if(moveResult != UNKNOWN && moveResult != DOOMED) {
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
            LOG.error("***********************");
            LOG.error("DOOMED!");
            LOG.error("***********************");
            return new MoveWithState(DOOMED, this);
        }

if(turn >= Snake.debugTurn){
    LOG.debug("HALT");
}

        if(possibleMoves.size() == 1){
            return possibleMoves.get(0);
        } else {
            /*if(foodPlaces.size() == 1){
                Point f = foodPlaces.get(0);
                for(MoveWithState m : possibleMoves){
                    Point p = m.getResPosForMyHead(this);
                    if(p.equals(f)){
                        return m;
                    }
                }
            }*/
            return getBestMove(possibleMoves);
        }
    }

    private MoveWithState getBestMove(ArrayList<MoveWithState> possibleMoves) {
        // ok we have plenty of alternative moves...
        // we should check, WHICH of them is the most promising...

        /*int moveFromPlanX = tryFollowMovePlan(possibleMoves);
        if (moveFromPlanX != UNKNOWN) {
            LOG.info("FOLLOW PLAN: "+getMoveIntAsString(moveFromPlanX));

            MoveWithState routeMove = intMovesToMoveKeysMap.get(moveFromPlanX);
            return possibleMoves.get(possibleMoves.indexOf(routeMove));
        }*/

        //1) only keep the moves with the highest DEEP...
        ArrayList<MoveWithState> keepOnlyWithHighDeep = filterStep01ByMaxDeept(possibleMoves);
        if(keepOnlyWithHighDeep.size() > 0) {
            possibleMoves = keepOnlyWithHighDeep;
            // ok only one option left - so let's use this...
            if (possibleMoves.size() == 1) {
                return possibleMoves.get(0);
            }
        }

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + possibleMoves);
}

        //2) remove all "toDangerous" moves (when we have better alternatives)
        boolean aMoveHasEscapeFromHazard = false;
        boolean aMoveHasEscapeFromBorder = false;
        boolean keepGoDanger = true;
        boolean keepGoNoGo = true;
        for (MoveWithState aMove : possibleMoves) {
            if (keepGoNoGo && !aMove.state.sEnterNoGoZone) {
                keepGoNoGo = false;
            }
            if (keepGoDanger && !aMove.state.sEnterDangerZone) {
                keepGoDanger = false;
            }

            // escape values will be used later in the code!
            if(!aMoveHasEscapeFromHazard){
                aMoveHasEscapeFromHazard = aMove.state.sEscapeFromHazard;
            }
            if(!aMoveHasEscapeFromBorder){
                aMoveHasEscapeFromBorder = aMove.state.sEscapeFromBorder;
            }
        }

        // do finally the filtering...
        ArrayList<MoveWithState> keepOnlyWithLowRisk = new ArrayList<>(possibleMoves);
        for (MoveWithState aMove : possibleMoves) {
            if (!keepGoNoGo && aMove.state.sEnterNoGoZone){
                keepOnlyWithLowRisk.remove(aMove);
            }
            if (!keepGoDanger && aMove.state.sEnterDangerZone){
                keepOnlyWithLowRisk.remove(aMove);
            }
        }
        if(keepOnlyWithLowRisk.size() > 0) {
            possibleMoves = keepOnlyWithLowRisk;
            // ok only one option left - so let's use this...
            if (possibleMoves.size() == 1) {
                return possibleMoves.get(0);
            }
        }

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + possibleMoves);
}

        MoveWithState possibleFoodMove = filterStep03FoodMove(possibleMoves);
        if(possibleFoodMove != null){
            return possibleFoodMove;
        }

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + possibleMoves);
}

        // filtering for least dangerous locations...
        TreeMap<Integer, ArrayList<MoveWithState>> groupByDirectThread = new TreeMap<>();
        for (MoveWithState aMove : possibleMoves) {
            Point resultingPos = aMove.getResPosForMyHead(this);
            // checking if we are under direct threat
            int aMoveRisk = snakeThisMovePossibleLocations[resultingPos.y][resultingPos.x];
            if(aMoveRisk > 0){
                if(aMoveRisk < myLen){
                    aMoveRisk = 0;
                } else if(aMoveRisk == myLen){
                    aMoveRisk = 1;
                }
            }
            ArrayList<MoveWithState> moves = groupByDirectThread.get(aMoveRisk);
            if(moves == null) {
                moves = new ArrayList<>();
                groupByDirectThread.put(aMoveRisk, moves);
            }
            moves.add(aMove);
        }
        ArrayList<MoveWithState> bestList = groupByDirectThread.firstEntry().getValue();
        if(bestList.size() == 1){
            return bestList.get(0);
        }

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + bestList);
}
        // 2a - checking if we can catch our own tail?!
        // in this case we can ignore the approach of other snake heads
        // but only if this will not move into hazard
        //if(mSoloMode || myLen > 19 || myLen >= maxOtherSnakeLen) {
        MoveWithState tailCatchMove = checkForCatchOwnTail(bestList);
        if (tailCatchMove != null) return tailCatchMove;
        //}

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + bestList);
}

        // checking if one of the moves will be a possible move target for TWO snakes! (if this is the case, then
        // this will typically end in a corner!
        ArrayList<MoveWithState> noneDoubleTargets = new ArrayList<>(bestList);
        boolean modifiedList = false;
        for(MoveWithState aMove: bestList){
            Point resPoint = aMove.getResPosForMyHead(this);
            if(snakeThisMovePossibleLocationList.containsKey(resPoint)){
                ArrayList<Integer> list = snakeThisMovePossibleLocationList.get(resPoint);
                if(list.size() > 1){
                    noneDoubleTargets.remove(aMove);
                    modifiedList = true;
                }
            }
        }
        if(modifiedList && noneDoubleTargets.size() > 0){
            bestList = noneDoubleTargets;
            if(bestList.size() == 1){
                MoveWithState aMove = bestList.get(0);
                if(!mHazardPresent || !aMove.state.sEnterHazardZone || isHazardFreeMove(aMove)){
                    return aMove;
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + bestList);
}

        if(foodGoForIt && foodActive != null){
            TreeMap<Integer, ArrayList<MoveWithState>> groupByResultingFoodDistance = new TreeMap<>();
            for (MoveWithState aMove : bestList) {
                Point resPos = aMove.getResPosForMyHead(this);
                int foodDistance = getPointDistance(foodActive, resPos);
                ArrayList<MoveWithState> list = groupByResultingFoodDistance.get(foodDistance);
                if(list == null){
                    list = new ArrayList<>();
                    groupByResultingFoodDistance.put(foodDistance, list);
                }
                list.add(aMove);
            }
            bestList = groupByResultingFoodDistance.firstEntry().getValue();
            if(bestList.size() == 1){
                return bestList.get(0);
            }

            // check if on of the moves is the opposite food direction?
            if(mFoodPrimaryDirection != -1) {
                ArrayList<MoveWithState> clone = new ArrayList<>(bestList);
                for (MoveWithState aMove : bestList) {
                    if(isOpposite(mFoodPrimaryDirection, aMove.move)){
                        clone.remove(aMove);
                    }
                }
                if(clone.size() > 0){
                    bestList = clone;
                }
                if(bestList.size() == 1){
                    MoveWithState aMove = bestList.get(0);
                    if(!mHazardPresent || !aMove.state.sEnterHazardZone || isHazardFreeMove(aMove)){
                        return aMove;
                    }
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + bestList);
}

        if(!mWrappedMode){
            TreeMap<Integer, ArrayList<MoveWithState>> groupByEnterBorderZone = new TreeMap<>();
            for (MoveWithState aMove : bestList) {
                Point resultingPos = aMove.getResPosForMyHead(this);
                int aMoveRisk = 0;
                // TODO: not only the BORDER - also the MIN/MAX can be not so smart...
                if (resultingPos.y == 0 || resultingPos.y == Y - 1) {
                    aMoveRisk++;
                }
                if (resultingPos.x == 0 || resultingPos.x == X - 1) {
                    aMoveRisk++;
                }

                ArrayList<MoveWithState> moves = groupByEnterBorderZone.get(aMoveRisk);
                if(moves == null) {
                    moves = new ArrayList<>();
                    groupByEnterBorderZone.put(aMoveRisk, moves);
                }
                moves.add(aMove);
            }
            bestList = groupByEnterBorderZone.firstEntry().getValue();
            if(bestList.size() == 1){
                return bestList.get(0);
            }
        }

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + bestList);
}

        // check if some moves have the "go border" active (and others not)
        boolean goToBorder = true;
        for(MoveWithState aMove: bestList){
            if(goToBorder) {
                goToBorder = aMove.state.sEnterBorderZone;
            }else{
                break;
            }
        }

        // not all entries have go-to-border...
        if(!goToBorder){
            ArrayList<MoveWithState> movesWithoutGoToBorder = new ArrayList<>(bestList);
            for(MoveWithState aMove: bestList){
                if(aMove.state.sEnterBorderZone){
                    Point resPoint = aMove.getResPosForMyHead(this);
                    if(isPosLocatedAtBorder(resPoint)) {
                        movesWithoutGoToBorder.remove(aMove);
                    }
                }
            }
            if(movesWithoutGoToBorder.size() > 0) {
                bestList = movesWithoutGoToBorder;
                // ok - only one option left... let's return that!
                if(bestList.size() == 1){
                    return bestList.get(0);
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + bestList);
}
        // comparing the hazard moves by its thread level
        if(mHazardPresent){
            multiplyHazardThreadsInMap();

            TreeMap<Integer, ArrayList<MoveWithState>> groupByHazardLevel = new TreeMap<>();
            for (MoveWithState aMove : bestList) {
                Point resPos = aMove.getResPosForMyHead(this);
                int hzdLevel = hazardZone[resPos.y][resPos.x];
                ArrayList<MoveWithState> list = groupByHazardLevel.get(hzdLevel);
                if(list == null){
                    list = new ArrayList<>();
                    groupByHazardLevel.put(hzdLevel, list);
                }
                list.add(aMove);
            }
            Map.Entry<Integer, ArrayList<MoveWithState>> bestEntry = groupByHazardLevel.firstEntry();
            bestList = bestEntry.getValue();
            if(bestList.size() == 1){
                if(bestEntry.getKey() == 1) {
                    // check if we really can get out here?!
                    MoveWithState aMove = bestList.get(0);
                    Point resPoint = aMove.getResPosForMyHead(this);
                    if(checkIfAnyMoveFromPointWillGetUsOutOfHazard(resPoint)){
                        // cool there is really a way out!
                        return bestList.get(0);
                    } else {
                        groupByHazardLevel.remove(bestEntry.getKey());
                        bestEntry = groupByHazardLevel.firstEntry();
                        if(bestEntry != null){
                            bestList = bestEntry.getValue();
                        }
                    }
                }else {
                    return bestList.get(0);
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////

        // checking if there is a GETAWAY from HAZARD or BORDER
        if(aMoveHasEscapeFromHazard) {
            for (MoveWithState aMove : bestList) {
                if (aMove.state.sEscapeFromHazard) {
                    return aMove;
                }
            }
        }
        if(aMoveHasEscapeFromBorder) {
            for (MoveWithState aMove : bestList) {
                if (aMove.state.sEscapeFromBorder) {
                    return aMove;
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////

        // cool to 'still' have so many options...
        if (bestList.size() == 2) {
            int move1 = bestList.get(0).move;
            int move2 = bestList.get(1).move;
            if(isOpposite(move1, move2)) {
                // OK - UP/DOWN or LEFT/RIGHT
                int op1, op2;
                if(mHazardPresent && hazardZone[myHead.y][myHead.x] > 0){
                    // ok we are currently IN HAZARD... which direction we should try to
                    // go OUT OF HAZARD?!s

                    op1 = countMovesTillOutOfHazard(myHead, move1, 0) -1;
                    op2 = countMovesTillOutOfHazard(myHead, move2, 0) -1;
                    // LOWER IS BETTER!!!
                    if(op1 > op2){
                        return bestList.get(1);
                    }else if(op2 > op1){
                        return bestList.get(0);
                    }
                }else{
                    int[][] finalMap = new int[Y][X];
                    finalMap[myHead.y][myHead.x] = 1;
                    for (int y = 0; y < X; y++) {
                        for (int x = 0; x < X; x++) {
                            if (myBody[y][x] > 0) {
                                finalMap[y][x] = 1;
                            } else if (snakeBodies[y][x] > 0) {
                                finalMap[y][x] = 1;
                            } else if (snakeThisMovePossibleLocations[y][x] > 0) {
                                finalMap[y][x] = 1;
                            }
                        }
                    }
                    boolean toRestore = enterNoGoZone;
                    enterNoGoZone = true;
                    op1 = countMoves(finalMap, myHead, move1, 0) - 1;
                    op2 = countMoves(finalMap, myHead, move2, 0) - 1;
                    enterNoGoZone = toRestore;

                    // HIGHER IS BETTER!!!
                    if(op1>op2){
                        return bestList.get(0);
                    }else if(op2>op1){
                        return bestList.get(1);
                    }
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////

if(turn >= Snake.debugTurn){
    LOG.debug("HALT" + bestList);
}

        // checking the default movement options from our initial implemented movement plan...
        // as fallback take the first entry from our list...
        if(mWrappedMode){
            // in wrapped mode there is NO CENTER!
            LOG.info("select RANDOM - RIIIIISIKO");
            return bestList.get((int) (bestList.size() * Math.random()));
        }else {
            int moveFromPlan = tryFollowMovePlanGoCenter(bestList);
            if (moveFromPlan != UNKNOWN) {
                MoveWithState routeMove = intMovesToMoveKeysMap.get(moveFromPlan);
                return bestList.get(bestList.indexOf(routeMove));
            } else {
                // ok still options
                LOG.info("select RANDOM - RIIIIISIKO");
                return bestList.get((int) (bestList.size() * Math.random()));
            }
        }
    }

    private ArrayList<MoveWithState> filterStep01ByMaxDeept(ArrayList<MoveWithState> possibleMoves) {
        int maxDeptWithOtherTargets = 0;
        int maxDeptWithoutOtherTargets = 0;
        for (MoveWithState aMove : possibleMoves) {
            int dept = aMove.state.sMAXDEEP;
            if(aMove.state.sIgnoreOtherTargets){
                maxDeptWithoutOtherTargets = Math.max(maxDeptWithoutOtherTargets, dept);
            }else{
                maxDeptWithOtherTargets = Math.max(maxDeptWithOtherTargets, dept);
            }
        }

        int maxDept = 0;
        boolean removeIgnoreOtherTargets = true;
        // there are moves that can be more promising (if the other snakes does not move into our way)
        if(maxDeptWithoutOtherTargets > 0) {
            if(maxDeptWithOtherTargets == 0){
                // ok we are in the situation, that all the possible moves have the "IGNORE OTHER MOVE"
                // flag...
                maxDept = maxDeptWithoutOtherTargets;
                removeIgnoreOtherTargets = false;
            } else if(maxDeptWithOtherTargets >= maxDeptWithoutOtherTargets) {
                maxDept = maxDeptWithOtherTargets;
                removeIgnoreOtherTargets = true;
            } else if(maxDeptWithOtherTargets >= myLen){
                maxDept = maxDeptWithOtherTargets;
                removeIgnoreOtherTargets = true;
            } else if(maxDeptWithoutOtherTargets > myLen && maxDeptWithoutOtherTargets > maxDeptWithOtherTargets){
                // so ignoring th other targets will give use at least the possibility to survive
                maxDept = maxDeptWithoutOtherTargets;
                removeIgnoreOtherTargets = false;
            } else{
                // we are "nearly doomed" anyhow - so what ever move we are going to choose we will not have
                // a chance to fit into the remaining space...
                maxDept = Math.min(maxDeptWithoutOtherTargets, maxDeptWithOtherTargets);
                removeIgnoreOtherTargets = false;
            }
        }else{
            // all possible moves can be made consider the moves of the other snakes as well...
            maxDept = maxDeptWithOtherTargets;
            removeIgnoreOtherTargets = true;
        }

        maxDept = Math.min(maxDept, (int) (myLen*1.4));

        // do finally the filtering...
        ArrayList<MoveWithState> keepOnlyWithHighDeep = new ArrayList<>(possibleMoves);
        for (MoveWithState aMove : possibleMoves) {
            int dept = aMove.state.sMAXDEEP;
            if (dept < maxDept) {
                keepOnlyWithHighDeep.remove(aMove);
            }else if(removeIgnoreOtherTargets && aMove.state.sIgnoreOtherTargets){
                keepOnlyWithHighDeep.remove(aMove);
            }
        }
        return keepOnlyWithHighDeep;
    }

    private MoveWithState filterStep03FoodMove(ArrayList<MoveWithState> possibleMoves) {
        if(mFoodPrimaryDirection != -1 && mFoodSecondaryDirection != -1){
            // TODO: decide for the better FOOD move...
            // checking if primary or secondary FOOD direction is possible
            // selecting the MOVE with less RISK (if there is one with)
            // avoid from border we can do so...
            MoveWithState priMove = intMovesToMoveKeysMap.get(mFoodPrimaryDirection);
            int priIdx = possibleMoves.indexOf(priMove);
            if(priIdx > -1){
                priMove = possibleMoves.get(priIdx);
            }else{
                priMove = null;
            }
            MoveWithState secMove = intMovesToMoveKeysMap.get(mFoodSecondaryDirection);
            int secIdx = possibleMoves.indexOf(secMove);
            if(secIdx > -1){
                secMove = possibleMoves.get(secIdx);
            }else{
                secMove = null;
            }

            if(secMove != null && priMove != null){
                // Compare possible distance to other's (to compare which is less risky)
                if( (secMove.state.sEscapeFromHazard && !priMove.state.sEscapeFromHazard)
                        ||  (secMove.state.sEscapeFromBorder && !priMove.state.sEscapeFromBorder)
                        ||  (!secMove.state.sEnterBorderZone && priMove.state.sEnterBorderZone)
                        ||  (!secMove.state.sEnterHazardZone && priMove.state.sEnterHazardZone)
                        ||  (!secMove.state.sEnterDangerZone && priMove.state.sEnterDangerZone
                        ||  (mHazardPresent && (isHazardFreeMove(secMove) && !isHazardFreeMove(priMove)) ))
                ){
                    // prefer secondary!
                    state = secMove.move;
                    return secMove;
                }else{
                    state = priMove.move;
                    return priMove;
                }
            } else if(priMove != null){
                state = priMove.move;
                return priMove;
            } else if(secMove != null){
                // if we can catch our TAIL, then
                if(turn > 250 && myHealth > 50){
                    MoveWithState tailCatchMove = checkForCatchOwnTail(possibleMoves);
                    if (tailCatchMove != null) return tailCatchMove;
                }
                // and only if we can't catch out tail. we make the second direction move...
                state = secMove.move;
                return secMove;
            }
        } else if(mFoodPrimaryDirection != -1) {
            MoveWithState pMove = intMovesToMoveKeysMap.get(mFoodPrimaryDirection);
            if (possibleMoves.contains(pMove)) {
                return possibleMoves.get(possibleMoves.indexOf(pMove));
            }
        }

        return null;
    }


    private boolean checkIfAnyMoveFromPointWillGetUsOutOfHazard(Point p) {
        boolean ret = isPossibleMoveOutOfHazard(getNewPointForDirection(p, UP));
        if(!ret){
            ret = isPossibleMoveOutOfHazard(getNewPointForDirection(p, DOWN));
        }
        if(!ret){
            ret = isPossibleMoveOutOfHazard(getNewPointForDirection(p, LEFT));
        }
        if(!ret){
            ret = isPossibleMoveOutOfHazard(getNewPointForDirection(p, RIGHT));
        }
        return ret;
    }

    private boolean isPossibleMoveOutOfHazard(Point p){
        try {
            return hazardZone[p.y][p.x] == 0 && myBody[p.y][p.x] == 0 && snakeBodies[p.y][p.x] == 0;
        }catch(IndexOutOfBoundsException iob){
            return false;
        }
    }

    private MoveWithState checkForCatchOwnTail(ArrayList<MoveWithState> moveList) {
        if(lastTurnTail != null){
            // checking if all moves will end @ border...
            if(!mSoloMode) {
                ArrayList<MoveWithState> movesWithoutGoToBorder = new ArrayList<>(moveList);
                for (MoveWithState aMove : moveList) {
                    if (aMove.state.sEnterBorderZone) {
                        Point resPoint = aMove.getResPosForMyHead(this);
                        if (isPosLocatedAtBorder(resPoint)) {
                            movesWithoutGoToBorder.remove(aMove);
                        }
                    }
                }
                if (movesWithoutGoToBorder.size() > 0) {
                    moveList = movesWithoutGoToBorder;
                    // ok - only one option left... let's return that!
                } else {
                    // ok all of the available moves will end at the border.. so
                    // we can accept the possible tail-catch
                }
            }

            for (MoveWithState aMove : moveList) {
                Point rPos = aMove.getResPosForMyHead(this);
                // see also 'isHazardFreeMove(aMove)'
                if(!mHazardPresent || hazardZone[rPos.y][rPos.x] == 0){
                    // cool - just lat pick that one!
                    if (rPos.equals(lastTurnTail)) {
                        return aMove;
                    }
                }
            }

            // second run...
            for (MoveWithState aMove : moveList) {
                Point rPos = aMove.getResPosForMyHead(this);
                // see also 'isHazardFreeMove(aMove)'
                if(!mHazardPresent || hazardZone[rPos.y][rPos.x]==0){
                    // cool - just lat pick that one!
                    if((getPointDistance(lastTurnTail, rPos) == 1 || getPointDistance(myTail, rPos) == 1) && !foodPlaces.contains(rPos)){
                        return aMove;
                    }
                }
            }

        }
        return null;
    }

    private int countMovesTillOutOfHazard(Point aPos, int move, int count) {
        Point nextPoint = getNewPointForDirection(aPos, move);
        switch (move){
            case UP:
            case DOWN:
                if(count > Y){
                    return count;
                }
                break;
            case LEFT:
            case RIGHT:
                if(count > X){
                    return count;
                }
                break;
        }
        try {
            if (hazardZone[nextPoint.y][nextPoint.x] > 0) {
                count = countMovesTillOutOfHazard(nextPoint, move, count);
            }
        }catch(IndexOutOfBoundsException iob){
            return count;
        }
        return ++count;
    }


    private int countMoves(int[][] map, Point aPos, int move, int count) {
        Point nextPoint = getNewPointForDirection(aPos, move);
        // to skip loop check!
        switch (move){
            case UP:
                if(count>Y){
                    return count;
                }else {
                    if (canMoveUp(aPos, map, 0)) {
                        count = countMoves(map, nextPoint, move, count);
                    }
                }
                break;

            case DOWN:
                if(count>Y){
                    return count;
                }else {
                    if (canMoveDown(aPos, map, 0)) {
                        count = countMoves(map, nextPoint, move, count);
                    }
                }
                break;

            case LEFT:
                if(count>X){
                    return count;
                }else {
                    if (canMoveLeft(aPos, map, 0)) {
                        count = countMoves(map, nextPoint, move, count);
                    }
                }
                break;

            case RIGHT:
                if(count>X){
                    return count;
                }else {
                    if (canMoveRight(aPos, map, 0)) {
                        count = countMoves(map, nextPoint, move, count);
                    }
                }
                break;
        }
        return ++count;
    }

    private boolean isOpposite(int i, int j) {
        return  (i==UP && j==DOWN) ||
                (j==UP && i==DOWN) ||
                (i==LEFT && j==RIGHT) ||
                (j==LEFT && i==RIGHT)
                ;
    }

    /*private TreeMap<Integer, ArrayList<MoveWithState>> groupByOtherHeadDistance(ArrayList<MoveWithState> bestList, ArrayList<PointWithBool> dangerousNextMovePositions) {
if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}
        TreeMap<Integer, ArrayList<MoveWithState>> returnMap = new TreeMap<>();
        for (MoveWithState aMove : bestList) {
            Point resultingPos = aMove.getResPosForMyHead(this);
            int minDist = 100;
            for(PointWithBool otherSnake: dangerousNextMovePositions) {
                int minEvalDistance = 3;

                // otherSnake.bool => snake have the same length then we do
                if(otherSnake.bool){
                    minEvalDistance = 2;
                }

                int faceToFaceDist = getPointDistance(otherSnake.point, resultingPos);
                if(faceToFaceDist == 2){
                    // will this end in a CAN BE CATCHED in NEXT Move?!
                    if(getPointXDistance(otherSnake.point, resultingPos) == 1){
                        faceToFaceDist = 1;
                    }
                }
                if (faceToFaceDist < minEvalDistance) {
                    minDist = Math.min(minDist, faceToFaceDist);
                }
            }

            ArrayList<MoveWithState> moves = returnMap.get(minDist);
            if(moves == null) {
                moves = new ArrayList<>();
                returnMap.put(minDist, moves);
            }
            moves.add(aMove);
        }
if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}
        return returnMap;
    }*/

    private int XtryFollowMovePlan(ArrayList<MoveWithState> finalMoveOptions) {
        ArrayList<MoveWithState> noBorder = new ArrayList<>(finalMoveOptions);
        for(MoveWithState aMove : finalMoveOptions){
            if(aMove.state.sEnterBorderZone){
                noBorder.remove(aMove);
            }
        }
        finalMoveOptions = noBorder;

        // now we can check, if we can follow the default movement plan...
        // for all the possible MOVE directions we might have to set our BoardBounds?!
        boolean canGoUp = finalMoveOptions.contains(intMovesToMoveKeysMap.get(UP));
        boolean canGoRight = finalMoveOptions.contains(intMovesToMoveKeysMap.get(RIGHT));
        boolean canGoDown = finalMoveOptions.contains(intMovesToMoveKeysMap.get(DOWN));
        boolean canGoLeft = finalMoveOptions.contains(intMovesToMoveKeysMap.get(LEFT));

if(turn >= Snake.debugTurn){
    LOG.debug("HALT");
}
        if(canGoUp && tPhase == 0) {
            if (myHead.x == xMax) {
                LOG.info("PONG");
                tPhase = 1;
                state = LEFT;
                return UP;
            } else if(myHead.x == xMin + 1) {
                LOG.info("PING");
                tPhase = 1;
                state = RIGHT;
                return UP;
            }
        }
        if(myHead.x == xMin){
            tPhase = 2;
        }else if(myHead.x > xMin + 1 && myHead.x < xMax){
            tPhase = 0;
        }
        if(canGoDown && tPhase == 2){
            state = DOWN;
            return DOWN;
        }

        if(finalMoveOptions.size() == 1){
            if(tPhase == 1) {
                tPhase = 0;
            }
            return finalMoveOptions.get(0).move;
        }


        switch (state) {
            case UP:
                if (canGoUp) {
                    state = UP;
                    return UP;
                } else{
                    return planDecideForRightOrLeft(canGoRight, canGoLeft);
                }

            case RIGHT:
                if (canGoRight) {
                    state = RIGHT;
                    return RIGHT;
                } else {
                    return planDecideForUpOrDown(canGoUp, canGoDown);
                }

            case DOWN:
                if(canGoDown){
                    state = DOWN;
                    return DOWN;
                }else{
                    return planDecideForRightOrLeft(canGoRight, canGoLeft);
                }

            case LEFT:
                if(canGoLeft){
                    state = LEFT;
                    return LEFT;
                }else{
                    return planDecideForUpOrDown(canGoUp, canGoDown);
                }
        }
        return UNKNOWN;
    }

    private int tryFollowMovePlanGoCenter(ArrayList<MoveWithState> finalMoveOptions) {
        LOG.info("head to center (cause no other priority could be found)");
        int targetX = X/2;
        int targetY = Y/2;
        boolean canGoUp     = finalMoveOptions.contains(intMovesToMoveKeysMap.get(UP));
        boolean canGoRight  = finalMoveOptions.contains(intMovesToMoveKeysMap.get(RIGHT));
        boolean canGoDown   = finalMoveOptions.contains(intMovesToMoveKeysMap.get(DOWN));
        boolean canGoLeft   = finalMoveOptions.contains(intMovesToMoveKeysMap.get(LEFT));

        if(myHead.y > myHead.x){
            if(myHead.y < targetY && canGoUp){
                return UP;
            }
            if(myHead.y > targetY && canGoDown){
                return DOWN;
            }
            if(myHead.x < targetX && canGoRight){
                return RIGHT;
            }
            if(myHead.x > targetX && canGoLeft){
                return LEFT;
            }
        }else{
            if(myHead.x < targetX && canGoRight){
                return RIGHT;
            }
            if(myHead.x > targetX && canGoLeft){
                return LEFT;
            }
            if(myHead.y < targetY && canGoUp){
                return UP;
            }
            if(myHead.y > targetY && canGoDown){
                return DOWN;
            }
        }
        return UNKNOWN;
    }

    private int tryFollowMovePlan(ArrayList<MoveWithState> finalMoveOptions) {
        LOG.info("follow our path... (cause no other priority could be found)");
        // now we can check, if we can follow the default movement plan...
        // for all the possible MOVE directions we might have to set our BoardBounds?!
        boolean canGoUp     = finalMoveOptions.contains(intMovesToMoveKeysMap.get(UP));
        boolean canGoRight  = finalMoveOptions.contains(intMovesToMoveKeysMap.get(RIGHT));
        boolean canGoDown   = finalMoveOptions.contains(intMovesToMoveKeysMap.get(DOWN));
        boolean canGoLeft   = finalMoveOptions.contains(intMovesToMoveKeysMap.get(LEFT));

        switch (state){
            case UP:
                if(canGoUp) {
                    // only move up till 1/3 of the board...
                    return UP;
                } else {
                    if (myHead.x < xMax / 2 || !canGoLeft){ //cmdChain.contains(LEFT)) {
                        state = RIGHT;
                        if(canGoRight){
                            return RIGHT;
                        }
                    } else {
                        state = LEFT;
                        if(canGoLeft){
                            return LEFT;
                        }
                    }
                }
                break;

            case RIGHT:
                if(canGoRight) {
                    return RIGHT;
                }else{
                    if (myHead.x == xMax && tPhase > 0) {
                        if (canGoDown && myHead.y == yMax) {
                            // we should NEVER BE HERE!!
                            // we are in the UPPER/RIGHT Corner while in TraverseMode! (something failed before)
                            LOG.info("WE SHOULD NEVER BE HERE in T-PHASE >0");
                            tPhase = 0;
                            state = DOWN;
                            return DOWN;
                        } else {
                            state = LEFT;
                            //OLD CODE:
                            //return moveUp();
                            // NEW
                            if(canGoUp){
                                return UP;
                            }
                        }
                    } else {
                        // NEW CODE... [when we are in the init phase - reached lower right corner
                        // we go to lower left corner]
                        if(myHead.y == yMin && tPhase == 0 && canGoLeft){
                            state = LEFT;
                            return LEFT;
                        }else {
                            int upOrDown = decideForUpOrDownUsedFromMoveLeftOrRight(canGoUp, canGoDown);
                            if(upOrDown > UNKNOWN) {
                                return upOrDown;
                            }
                        }
                    }
                }
                break;

            case DOWN:
                if(canGoDown){
                    if (canGoRight && tPhase == 2 && myHead.y == yMin + 1) {
                        tPhase = 1;
                        state = RIGHT;
                        return RIGHT;
                    } else {
                        return DOWN;
                    }
                } else{
                    if (canGoRight && tPhase > 0) {
                        state = RIGHT;
                        return RIGHT;
                    } else {
                        if (canGoRight && (myHead.x < xMax / 2 || !canGoLeft)) { //cmdChain.contains(LEFT)) {
                            state = RIGHT;
                            return RIGHT;
                        } else if(canGoLeft){
                            state = LEFT;
                            return LEFT;
                        }else{
                            // looks lke we can't go LEFT or RIGHT...
                            // and we CAN NOT GO DOWN :-/
                        }
                    }
                }
                break;

            case LEFT:
                if(canGoLeft) {

                    // even if we "could" move to left - let's check, if we should/will follow our program...
                    if (myHead.x == xMin + 1) {
                        // We are at the left-hand "border" side of the board
                        if (tPhase != 2) {
                            tPhase = 1;
                        }
                        if (myHead.y == yMax) {
                            state = DOWN;
                            return LEFT;
                        } else {
                            if (canGoUp) {
                                state = RIGHT;
                                return UP;
                            } else {
                                return LEFT;
                            }
                        }
                    } else if ((yMax - myHead.y) % 2 == 1) {
                        // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                        // we simply really move to the LEFT (since we can!))
                        if (canGoUp) {
                            tPhase = 2;
                            return UP;
                        } else {
                            return LEFT;
                        }
                    } else {
                        return LEFT;
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
                            state = DOWN;
                            //return Snake.L;
                            //OLD CODE:
                            //return moveDown();
                            // NEW
                            if (canGoDown) {
                                return DOWN;
                            }else{
                                // TODO ?!
                            }
                        } else {
                            if (canGoRight) {
                                state = RIGHT;
                                return RIGHT;
                            } else if (canGoUp) {
                                state = RIGHT;
                                return UP;
                            }
                        }
                    }else if(myHead.x == xMax){
                        if (canGoLeft){
                            state = LEFT;
                            return LEFT;
                        }else if (canGoUp) {
                            state = LEFT;
                            return UP;
                        }

                    } else {
                        if ((yMax - myHead.y) % 2 == 1) {
                            // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                            // we simply really move to the LEFT (since we can!))
                            if (canGoUp) {
                                tPhase = 2;
                                return UP;
                            } else {
                                //return Snake.L;
                                //OLD CODE:
                                //return moveDown();

                                // NEW
                                if(canGoDown){
                                    return DOWN;
                                }
                            }
                        } else {
                            // return Snake.L;
                            // if we are in the pending mode, we prefer to go ALWAYS UP
                            int upOrDown = decideForUpOrDownUsedFromMoveLeftOrRight(canGoUp, canGoDown);
                            if(upOrDown > UNKNOWN) {
                                return upOrDown;
                            }
                        }
                    }
                }
                break;
        }
        return UNKNOWN;
    }

    private int decideForUpOrDownUsedFromMoveLeftOrRight(boolean canGoUp, boolean canGoDown) {
        // if we are in the pending mode, we prefer to go ALWAYS-UP
        if (tPhase > 0 && canGoUp && myHead.y < yMax) {
            state = UP;
            return UP;
        } else {
            if (canGoUp && (myHead.y < yMax / 2 || !canGoDown)) {
                state = UP;
                return UP;
            } else if (canGoDown){
                state = DOWN;
                return DOWN;
            }
        }
        return UNKNOWN;
    }

    private int planDecideForRightOrLeft(boolean canGoRight, boolean canGoLeft) {
        // if we are in the pending mode, we prefer to go ALWAYS-UP
        if (canGoRight && (myHead.x < xMax / 2 || !canGoLeft)) {
            state = RIGHT;
            return RIGHT;
        } else if (canGoLeft){
            state = LEFT;
            return LEFT;
        }
        return UNKNOWN;
    }

    private int planDecideForUpOrDown(boolean canGoUp, boolean canGoDown) {
        // if we are in the pending mode, we prefer to go ALWAYS-UP
        if (canGoUp && (myHead.y < yMax / 2 || !canGoDown)) {
            state = UP;
            return UP;
        } else if (canGoDown){
            state = DOWN;
            return DOWN;
        }
        return UNKNOWN;
    }
}
