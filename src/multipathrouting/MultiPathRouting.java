package net.floodlightcontroller.multipathrouting;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.LinkedList;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.core.IFloodlightProviderService;

import net.floodlightcontroller.multipathrouting.LinkWithCost;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class MultiPathRouting implements IFloodlightModule ,ITopologyListener, IMultiPathRoutingService{
	protected static Logger logger;
	protected IFloodlightProviderService floodlightProvider;
	protected ITopologyService topologyService;
	protected final int ROUTE_LIMITATION = 20;
	//Double map, HashMap< ClusterDpid, HashMap<switchDpid, Set<Links>>>
	protected HashMap<Long, HashMap<Long, HashSet<LinkWithCost>>> dpidLinks;
    
	protected class PathCacheLoader extends CacheLoader<RouteId, MultiRoute> {
        MultiPathRouting mpr;
        PathCacheLoader(MultiPathRouting mpr) {
            this.mpr = mpr;
        }

        @Override
        public MultiRoute load(RouteId rid) {
            return mpr.buildMultiRoute(rid);
        }
	}
    private final PathCacheLoader pathCacheLoader = new PathCacheLoader(this);
    protected LoadingCache<RouteId, MultiRoute> pathcache;

	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates){
		//https://github.com/LucaPrete/GreenMST/blob/master/src/it/garr/greenmst/GreenMST.java
		for (LDUpdate update : linkUpdates){
		//	logger.error("Received topology update event {}.", update);
			if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_REMOVED) || update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_UPDATED)) {
            	Long island = topologyService.getL2DomainId(update.getSrc());
				LinkWithCost srcLink = new LinkWithCost(update.getSrc(), update.getSrcPort(), update.getDst(), update.getDstPort(),1);
				LinkWithCost dstLink = srcLink.getInverse();
				if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_REMOVED)){
					removeLink(island,srcLink);
					removeLink(island,dstLink);
				}
				else if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_UPDATED)){
					addLink(island,srcLink);
					addLink(island,dstLink);
				}
			}
			else if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.SWITCH_UPDATED) || update.getOperation().equals(ILinkDiscovery.UpdateOperation.SWITCH_REMOVED)){
            	Long island = topologyService.getL2DomainId(update.getSrc());
				removeDpidLinks(island,update.getSrc());
			}
		} 

	}
	public void removeDpidLinks(Long island,Long dpid){
		if ( null == dpidLinks.get(island)){
			return ;
		}
		else if ( null == dpidLinks.get(island).get(dpid)){
			return ;
		}
		else{
			dpidLinks.get(island).get(dpid).clear();
		}
	}
	public void removeLink(Long island,LinkWithCost link){
		Long dpid = link.getSrcDpid();
		if( null == dpidLinks.get(island)){
			return;
		}
		else if (null == dpidLinks.get(island).get(dpid)){
			return;
		}
		else{
			dpidLinks.get(island).get(dpid).remove(link);
		}

	}
	public void addLink(Long island,LinkWithCost link){	
		Long dpid = link.getSrcDpid();
		if( null == dpidLinks.get(island)){
			HashMap<Long, HashSet<LinkWithCost>> dpidWithLinks = new HashMap<Long, HashSet<LinkWithCost>>();
			HashSet<LinkWithCost> links = new HashSet<LinkWithCost>();
			links.add(link);
			dpidWithLinks.put(dpid,links);
			dpidLinks.put(island,dpidWithLinks);
		}
		else if (null == dpidLinks.get(island).get(dpid)){
			HashSet<LinkWithCost> links = new HashSet<LinkWithCost>();
			links.add(link);
			dpidLinks.get(island).put(dpid,links);
		}
		else{
			dpidLinks.get(island).get(dpid).add(link);
		}
	}
	public MultiRoute buildMultiRoute(RouteId rid){
		return computeMultiPath(rid.getSrc(),rid.getDst());
	}

	public MultiRoute computeMultiPath(long srcDpid,long dstDpid){
		Long island = topologyService.getL2DomainId(srcDpid);
		if( null == dpidLinks.get(island) || island != topologyService.getL2DomainId(dstDpid) || srcDpid == dstDpid)
			return null;
		if( null == dpidLinks.get(island).get(srcDpid) || null == dpidLinks.get(island).get(dstDpid))
			return null;
		HashMap<Long, HashSet<LinkWithCost>> previous = new HashMap<Long, HashSet<LinkWithCost>>();
		HashMap<Long, HashSet<LinkWithCost>> links = dpidLinks.get(island);
		HashMap<Long, Integer> costs = new HashMap<Long, Integer>();
		for(Long dpid : links.keySet()){
			costs.put(dpid,Integer.MAX_VALUE);
			previous.put(dpid,new HashSet<LinkWithCost>());
		}

		PriorityQueue<NodeCost> nodeq = new PriorityQueue<NodeCost>();
		HashSet<Long> seen = new HashSet<Long>();
		nodeq.add(new NodeCost(srcDpid,0));
		NodeCost node;
		while( null != nodeq.peek()){
			node = nodeq.poll();
			if(node.getDpid() ==  dstDpid)
				break;
			int cost = node.getCost();
			seen.add(node.getDpid());
			for (LinkWithCost link: links.get(node.getDpid())) {
				Long dst = link.getDstDpid();
				int totalCost = link.getCost() + cost;
				if( true == seen.contains(dst))
					continue;

				if( totalCost < costs.get(dst) ){
					costs.put(dst,totalCost);

					previous.get(dst).clear();
					previous.get(dst).add(link.getInverse());
				}
				else if( totalCost == costs.get(dst) ){
					//multiple path
					previous.get(dst).add(link.getInverse());
				}

				NodeCost ndTemp = new NodeCost(dst,totalCost);
				nodeq.remove(ndTemp);
				nodeq.add(ndTemp);

			}

		}
		MultiRoute routes = new MultiRoute();
		for(int i=0;i<ROUTE_LIMITATION;i++){
			if(previous.get(dstDpid).size() <=0)
				break;
        	LinkedList<NodePortTuple> switchPorts = new LinkedList<NodePortTuple>();
			if( false == generateMultiPath(srcDpid,dstDpid,previous,switchPorts))
				break;
			else{
				Route result = new Route(new RouteId(srcDpid,dstDpid), switchPorts);
				routes.addRoute(result);
			}

		}
		if ( 0  == routes.getRouteSize())
			return null;
		else
			return routes;
	}
	public boolean generateMultiPath(Long srcDpid, Long current, HashMap<Long, HashSet<LinkWithCost>> previous,LinkedList<NodePortTuple> switchPorts){
		if( current == srcDpid)
			return true;
		HashSet<LinkWithCost> links = previous.get(current);
		for(LinkWithCost link: links){
		 	if( true == generateMultiPath(srcDpid, link.getDstDpid(), previous,switchPorts)){
            	NodePortTuple npt = new NodePortTuple(link.getDstDpid(), link.getDstPort());
				switchPorts.addLast(npt);
     	    	npt = new NodePortTuple(link.getSrcDpid(), link.getSrcPort());
				switchPorts.addLast(npt);
				links.remove(link);
				return true;
			}
		}
		return false;
	}

	private void updateLinkCost(long srcDpid,long dstDpid,int cost){
		Long island = topologyService.getL2DomainId(srcDpid);
		if( null != dpidLinks.get(island) && null != dpidLinks.get(island).get(srcDpid)){
			for(LinkWithCost link: dpidLinks.get(island).get(srcDpid)){
				if(link.getSrcDpid() == srcDpid && link.getDstDpid() == dstDpid){
					link.setCost(cost);
					return;
				}
			}
		}
	}

	public MultiRoute getRoute(long srcDpid,long dstDpid){
        // Return null route if srcDpid equals dstDpid
        if (srcDpid == dstDpid) return null;


        RouteId id = new RouteId(srcDpid, dstDpid);
        MultiRoute results = null;

        try {
            results = pathcache.get(id);
        } catch (Exception e) {
            logger.error("{}", e);
        }
        return results;
	}
	//
	//IMultiPathRoutingService implement
	//

	//Service
	@Override
	public Route getRoute(long srcDpid,short srcPort,long dstDpid,short dstPort){

        // Return null the route source and desitnation are the
        // same switchports.
        if (srcDpid == dstDpid && srcPort == dstPort)
            return null;

        List<NodePortTuple> nptList;
        NodePortTuple npt;
        MultiRoute routes = getRoute(srcDpid, dstDpid);
		Route result;
		if ( null == routes){
			logger.error(" no result for {} to {}",srcDpid,dstDpid);
			result = null;
		}
		else{
			result = routes.getRoute();
		}
        if (routes == null && srcDpid != dstDpid) return null;


        if (result != null) {
            nptList= new ArrayList<NodePortTuple>(result.getPath());
        } else {
            nptList = new ArrayList<NodePortTuple>();
        }

        npt = new NodePortTuple(srcDpid, srcPort);
        nptList.add(0, npt); // add src port to the front
        npt = new NodePortTuple(dstDpid, dstPort);
        nptList.add(npt); // add dst port to the end

        RouteId id = new RouteId(srcDpid, dstDpid);
        result = new Route(id, nptList);
		logger.error("route = {}",result.toString());
        return result;
		
	}
	@Override
	public void modifyLinkCost(Long srcDpid,Long dstDpid,short cost){
		updateLinkCost(srcDpid,dstDpid,cost);
		updateLinkCost(dstDpid,srcDpid,cost);
	}


	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IMultiPathRoutingService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m =
            new HashMap<Class<? extends IFloodlightService>,
                IFloodlightService>();
        m.put(IMultiPathRoutingService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
        new ArrayList<Class<? extends IFloodlightService>>();
   		l.add(IFloodlightProviderService.class);
		l.add(ITopologyService.class);
	    return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		topologyService    = context.getServiceImpl(ITopologyService.class);
		logger = LoggerFactory.getLogger(MultiPathRouting.class);
		dpidLinks = new HashMap<Long, HashMap<Long, HashSet<LinkWithCost>>>();
        pathcache = CacheBuilder.newBuilder().concurrencyLevel(4)
                    .maximumSize(1000L)
                    .build(
                            new CacheLoader<RouteId, MultiRoute>() {
                                public MultiRoute load(RouteId rid) {
                                    return pathCacheLoader.load(rid);
                                }
                            });
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		topologyService.addListener(this);
	}

}
