package lejos.robotics.pathfinding;

import java.util.ArrayList;
import java.util.Collection;

import lejos.robotics.navigation.WayPoint;

/**
 * TODO: Might need OpenNodeSet (interface and algorithms) and ClosedNodeSet (class)? 
 * OpenNodeSet replaces NodeGenerator???
 * TODO: Overkill to extend WayPoint? Needs to use very little memory! Just x, y and neighbors! Minimal. Maybe some
 * static methods for handling nodes. Methods for getting waypoints. Extend Point2D instead?
 * @author BB
 *
 */
public class Node extends WayPoint {
	
	private float h_score = 0;
	private float g_score = 0;
	private Node cameFrom = null;
	private ArrayList <Node> neighbors = new ArrayList<Node>();
	private String id = null;
	
	public Node(String id, float x, float y) {
		super(x, y);
		this.id = id;
		// TODO: Get rid of string id? Kind of a memory hog with lots of generated nodes.
	}
	
	public Collection <Node> getNeighbors() {
		return neighbors;
	}
	
	/**
	 * Indicates the number of neighbors (nodes connected to this node).
	 * @return int Number of neighbors.
	 */
	public int neighbors() {
		return neighbors.size();
	}
	
	// Note: You have to add this node to neighbor, and then add neighbor to this node. This method doesn't do both.
	public void addNeighbor(Node neighbor) {
		// TODO: Maybe code here should add each other as neighbors?
		// TODO: Check to make sure same isn't added twice?
		// TODO: Check to make sure doesn't add itself? Return boolean.
		neighbors.add(neighbor);
	}
	
	// Note: You have to add this node to neighbor, and then add neighbor to this node. This method doesn't do both.
	public boolean removeNeighbor(Node neighbor) {
		// TODO: Maybe code here should add each other as neighbors? Should also check make sure isn't added twice.
		return neighbors.remove(neighbor);
	}
	
	
	public String getId() {
		return id;
	}
	
	public void setHeuristicEstimate(float h) {
		h_score = h;
	}
	
	public void setCost(float g) {
		g_score = g;
	}
	
	public float getCost(){
		return g_score;
	}
	
	// You can not set FScore because it is generated by adding the gscore and hscore.
	public float getFScore(){
		return g_score + h_score;
	}
	
	public Node getPredecessor() {
		return cameFrom;
	}
	
	public void setPredecessor(Node orig) {
		cameFrom = orig;
	}
	 
	// They want recursion here according to Wikipedia algorithm.
	public static void reconstructPath(Node current_node, Node start, Collection <WayPoint> path){
		if(current_node == start) {
			path.add(current_node);
			return; 
		} else reconstructPath(current_node.getPredecessor(), start, path);
		path.add(current_node);
		return;
	}

}