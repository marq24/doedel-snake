package com.emb.bs.ite;

public class MoveWithState {

    int move;
    Session.SavedState state;
    private Point resultingPos;

    public MoveWithState(int move) {
        this.move = move;
    }

    public MoveWithState(int move, Session s) {
        this.move = move;
        if(s!=null) {
            state = s.saveState();
        }
    }

    public Point getResPosForMyHead(Session s){
        if(resultingPos == null){
            resultingPos = s.getNewPointForDirection(s.myHead, move);
        }
        return  resultingPos;
    }

    //private HashMap<Point, Point> resultingPositionMap;
    /*public Point getResultingPos(Point startPos, Session s){
        if(resultingPositionMap == null){
            resultingPositionMap = new HashMap<>();
        }
        if(resultingPositionMap.containsKey(startPos)){
            return  resultingPositionMap.get(startPos);
        }else{
            Point resPos = s.getNewPointForDirection(startPos, move);
            resultingPositionMap.put(startPos, resPos);
            return resPos;
        }
    }*/

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof MoveWithState){
            return move == ((MoveWithState) obj).move;
        }else if(obj instanceof Integer) {
            return move == ((Integer) obj).intValue();
        }else{
            return super.equals(obj);
        }
    }

    @Override
    public String toString() {
        return Session.getMoveIntAsString(move) + " ["+state.toString()+"]";
    }
}
