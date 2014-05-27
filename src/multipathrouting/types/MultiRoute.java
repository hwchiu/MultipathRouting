package net.floodlightcontroller.multipathrouting;

import java.util.ArrayList;

import net.floodlightcontroller.routing.Route;

public class MultiRoute {
	protected short routeCount;
	protected ArrayList<Route> routes;

	public MultiRoute(){
		routeCount = 0;
		routes = new ArrayList<Route>();
	}
	
	public Route getRoute(){
		return null;
	}

	public short getRouteCount(){
		return routeCount;
	}

	public void addRoute(Route route){
		routeCount++;
		routes.add(route);
	}
}
