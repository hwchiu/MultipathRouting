package net.floodlightcontroller.multipathrouting;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;

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
import net.floodlightcontroller.core.IFloodlightProviderService;

import net.floodlightcontroller.multipathrouting.LinkWithCost;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class MultiPathRouting implements IFloodlightModule ,ITopologyListener{
	protected static Logger logger;
	protected IFloodlightProviderService floodlightProvider;
	protected ITopologyService topologyService;
	//Double map, HashMap< ClusterDpid, HashMap<switchDpid, Set<Links>>>
	protected HashMap<String, HashMap<String, HashSet<LinkWithCost>>> dpidLinks;

	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates){
		//https://github.com/LucaPrete/GreenMST/blob/master/src/it/garr/greenmst/GreenMST.java
		for (LDUpdate update : linkUpdates){
			logger.error("Received topology update event {}.", update);
			if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_REMOVED) || update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_UPDATED)) {
            	String island = HexString.toHexString(topologyService.getL2DomainId(update.getSrc()));
				LinkWithCost srcLink = new LinkWithCost(update.getSrc(), update.getSrcPort(), update.getDst(), update.getDstPort(),0);
				logger.error("outPort {}",update.getSrcPort());
				LinkWithCost dstLink = srcLink.getInverse();
				if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_REMOVED)){
					printDpidLinks();
				}
				else if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_UPDATED)){
					addLinks(island,srcLink);
					addLinks(island,dstLink);
				}
			}
		} 

	}
	public void printDpidLinks(){
		for(String myisland : dpidLinks.keySet()){
			logger.error("island = "+myisland);
			for(String dpid : dpidLinks.get(myisland).keySet()){
				logger.error("switch = "+dpid);
				for(LinkWithCost link: dpidLinks.get(myisland).get(dpid)){
					logger.error(link.toString());
				}

			}
		}

	}
	public void addLinks(String island,LinkWithCost link){	
		String dpid = HexString.toHexString(link.getSrcDpid());
		if( null == dpidLinks.get(island)){
			HashMap<String, HashSet<LinkWithCost>> dpidWithLinks = new HashMap<String, HashSet<LinkWithCost>>();
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

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
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
		dpidLinks = new HashMap<String, HashMap<String, HashSet<LinkWithCost>>>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		topologyService.addListener(this);
	}

}
