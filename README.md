Introduction
===========
MultiPathRouting module is a [Floodlight](https://github.com/floodlight/floodlight) Module, using the dijkstra algorithm to find multiple shortest path over a network.
It also modify the original forwarding modules and make it use the MultiPathRouting to forward packet.
The cost of each link is 1 by default and you can use the IMultiPathRoutingService API to modify the link cost dynamically.

Dispatch
=======
This module use the RoundRobin to dispatch multiple shortest path, you can find it and modify it [here](https://github.com/hwchiu/MultipathRouting/blob/master/src/multipathrouting/types/MultiRoute.java#L18)

Installation and configuration
=============================
    1. Copy the multipathrouting directory into floodlight/src/main/java/net/floodlightcontroller/.
    2. Replace the original Forwarding.java with forwarding/Forwarding.jave.
    3. Append the `net.floodlightcontroller.multipathrouting.MultiPathRouting` into `src/main/resources/META-INF/services /net.floodlightcontroller.core.module.IFloodlightModule`
    4. Modfiy the floodlight config file, default is `src/main/resources/floodlightdefault.properties`
        * Append `net.floodlightcontroller.multipathrouting.MultiPathRouting` to the option `floodlight.modules`
    5. Rebuild and restart Floodlight controller  

Service
======
This module provides a floodlight service interface which offers two functions now.  
	1.modifyLinkCost(Long srcDpid,Long dstDpid,short cost);
		You can call this function to modify the cost of the link between two switches.    
	2.getRoute(long srcDpid,short srcPort,long dstDpid,short dstPort);
        You can use this function to get one of multiple shortest path. 

Testing
======
	This module has been tested with Mininet, you can find the testing topology on the script directory.

Todo
====
    - Adding Restful API
    - Write Testing Code
    
