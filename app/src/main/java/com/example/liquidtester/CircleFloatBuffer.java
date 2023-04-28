package com.example.liquidtester;

public class CircleFloatBuffer {
    private float[] buff = null;
    private int capacity = 10;
    private int idx = 0;

    public CircleFloatBuffer(int cap){
        this.capacity = cap;
        this.buff = new float[cap];
    }

    public void put(float num){
        if (this.idx >= this.capacity){
            this.idx = 0;
        }
        this.buff[this.idx] = num;
        this.idx += 1;
    }

    public float mean(){
        float sum = 0;
        for (int i = 0; i < this.capacity; i ++){
            sum += this.buff[i];
        }
        return sum / this.capacity;
    }
}
