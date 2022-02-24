package com.battlesnake.starter;

import com.fasterxml.jackson.databind.JsonNode;

public class Point {
    int x,y;

    public Point(JsonNode p) {
        y = p.get("y").asInt();
        x = p.get("x").asInt();
    }

    public Point(int y, int x) {
        this.y = y;
        this.x = x;
    }

    public String toString(){
        return x+"|"+y;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Point){
            return y == ((Point) obj).y && x == ((Point) obj).x;
        }
        return super.equals(obj);
    }

    @Override
    protected Point clone() {
        return new Point(y, x);
    }
}
