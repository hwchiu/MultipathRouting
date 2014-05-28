###Introduction
    Implement a multiple shortest path routing alogorithm based on [Floodlight](https://github.com/floodlight/floodlight)

    
    
###Dispatch
    This module use the RoundRobin to dispatch multiple shortest path, you can find it and modify it [here](https://github.com/hwchiu/MultipathRouting/blob/master/src/multipathrouting/types/MultiRoute.java#L18)


###Installation and configuration
    1. Copy the multipathrouting directory into floodlight/src/main/java/net/floodlightcontroller/.
    2. Replace the original Forwarding.java with forwarding/Forwarding.jave.
    3. Append the `net.floodlightcontroller.multipathrouting.MultiPathRouting` into `src/main/resources/META-INF/services /net.floodlightcontroller.core.module.IFloodlightModule`
    4. Modfiy the floodlight config file, default is `src/main/resources/floodlightdefault.properties`
        * Append `net.floodlightcontroller.multipathrouting.MultiPathRouting` to the option `floodlight.modules`
    5. Rebuild and restart Floodlight controller

###Service
    This module provides a floodlight service interface which offers two functions now.
    - modifyLinkCost(Long srcDpid,Long dstDpid,short cost);
        You can call this function to modify the cost of the link between two switches.    
    - getRoute(long srcDpid,short srcPort,long dstDpid,short dstPort);
        You can use this function to get one of multiple shortest path. 

###Todo
    - Adding Restful API
    - Write Testing Code
    
