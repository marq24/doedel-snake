package com.emb.bs.ite;

public class MoveWithState {
    int move;
    Session.SavedState state;

    public MoveWithState(int move) {
        this.move = move;
    }

    public MoveWithState(int move, Session s) {
        this.move = move;
        if(s!=null) {
            state = s.saveState();
        }
    }

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
