/*
 * Copyright (c) 2016 Ericsson Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.sfc.ofrenderer.processors;

import java.util.List;
import java.util.Optional;

import org.opendaylight.sfc.genius.util.SfcGeniusRpcClient;
import org.opendaylight.sfc.ofrenderer.processors.SffGraph.SffGraphEntry;
import org.opendaylight.sfc.ofrenderer.utils.SfcLogicalInterfaceOfUtils;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarder.base.SffDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sl.rev140701.DataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.DpnIdType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.LogicalInterfaceLocator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;

/**
 * RSP processor class for RSPs which use Logical SFFs
 * Transport protocols are the same than in the NshEth processor (that is,
 * Eth+NSH between the SFF and SF, and VXGPE+NSH between the SFFs)
 * Differences with NSH-ETH processor:
 *
 *  - Even though method signatures use both Sff and Sf data plane locators (for keeping the
 *    interfaces defined in the base transport processor), they are not used in
 *    general (Logical SFFs don't have DPLs; SFs connected to logical SFFs use
 *    Logical Interfaces as DPLs). Instead, the data plane node ids for the switches
 *    participating in the hop (stored in the SffGraphEntry container) are extensively used.
 * - Transport egress actions are provided by Genius
 *
 * In the future when the RspManager is finished, we
 * wont have to mix transports in this class, as it will be called per hop.
 *
 * @author ediegra
 *
 */
public class SfcRspProcessorLogicalSff extends SfcRspTransportProcessorBase {

    //
    // TransportIngress methods
    //

    /*
     * Configure the Transport Ingress flow for SFs
     * Not needed since the same flow will be created in configureSffTransportIngressFlow()
     *
     * @param entry - RSP hop info used to create the flow
     */
    @Override
    public void configureSfTransportIngressFlow(SffGraphEntry entry, SfDataPlaneLocator sfDpl) {
    }

    /*
     * Configure the Transport Ingress flow for SFFs
     *
     * @param entry - RSP hop info used to create the flow
     * @param dstSffDpl - Not used in this processor
     */
    @Override
    public void configureSffTransportIngressFlow(SffGraphEntry entry, SffDataPlaneLocator dstSffDpl) {
        this.sfcFlowProgrammer.configureNshVxgpeTransportIngressFlow(
                sfcProviderUtils.getSffOpenFlowNodeName(entry.getDstSff(), entry.getPathId(), entry.getDstDpnId()),
                entry.getPathId(),
                entry.getServiceIndex());
    }

    //
    // PathMapper methods - not needed for NSH
    //

    /*
     */
    @Override
    public void configureSfPathMapperFlow(SffGraphEntry entry, SfDataPlaneLocator sfDpl) {
    }

    /*
     */
    @Override
    public void configureSffPathMapperFlow(SffGraphEntry entry, DataPlaneLocator hopDpl) {
    }

    //
    // NextHop methods
    //
    /**
     * Configure the Next Hop flow from an SFF to an SF
     *
     * @param entry - RSP hop info used to create the flow
     * @param srcSffDpl - not used in this processor
     * @param dstSfDpl - not used in this processor
     */
    @Override
    public void configureNextHopFlow(SffGraph.SffGraphEntry entry,
                                     SffDataPlaneLocator srcSffDpl,
                                     SfDataPlaneLocator dstSfDpl) {

        Optional<MacAddress> theMacAddr = getMacAddress(dstSfDpl);
        if (!theMacAddr.isPresent()) {
            throw new RuntimeException("Failed on mac address retrieval for dst SF dpl [" + dstSfDpl + "]");
        }
        this.sfcFlowProgrammer.configureNshEthNextHopFlow(
                sfcProviderUtils.getSffOpenFlowNodeName(entry.getDstSff(),
                        entry.getPathId(), entry.getDstDpnId()),
                theMacAddr.get().getValue(), entry.getPathId(),
                entry.getServiceIndex());
    }

    /*
     * Configure the Next Hop flow from an SF to an SFF
     */
    @Override
    public void configureNextHopFlow(SffGraphEntry entry, SfDataPlaneLocator srcSfDpl, SffDataPlaneLocator dstSffDpl) {
        // SF-SFF nexthop is not needed in logical SFF
        // (the underlying tunnels already have ips setted in the tunnel mesh, only transport
        // egress port selection is required)
    }

    /*
     * Configure the Next Hop flow from an SFF to an SF
     *
     * @param entry - RSP hop info used to create the flow
     * @param srcSfDpl - not used in this processor
     * @param dstSfDpl - the particular SF DPL used to create the flow
     */
    @Override
    public void configureNextHopFlow(SffGraphEntry entry, SfDataPlaneLocator srcSfDpl, SfDataPlaneLocator dstSfDpl) {

        Optional<MacAddress> dstSfMac = getMacAddress(dstSfDpl);
        if (!dstSfMac.isPresent()) {
            throw new RuntimeException("Failed on mac address retrieval for dst SF dpl [" + dstSfDpl + "]");
        }
        this.sfcFlowProgrammer.configureNshEthNextHopFlow(
                sfcProviderUtils.getSffOpenFlowNodeName(entry.getSrcSff(), entry.getPathId(), entry.getDstDpnId()),
                dstSfMac.get().getValue(),
                entry.getPathId(),
                entry.getServiceIndex());
    }

    /*
     * Configure the Next Hop flow from an SFF to an SFF
     *
     */
    @Override
    public void configureNextHopFlow(SffGraphEntry entry, SffDataPlaneLocator srcSffDpl, SffDataPlaneLocator dstSffDpl) {
        // SFF-SFF nexthop is not needed in logical SFF
        // (the underlying tunnels already have ips setted in the tunnel mesh, only port selection
        //is required)
    }


    //
    // TransportEgress methods
    //

    /*
     * Configure the Transport Egress flow from an SFF to an SF
     *
     * @param entry - RSP hop info used to create the flow
     * @param srcSffDpl - not used in this processor
     * @param dstSfDpl - not used in this processor
     * @param hopDpl - not used in this processor
     */
    @Override
    public void configureSfTransportEgressFlow(SffGraphEntry entry, SffDataPlaneLocator srcSffDpl,
            SfDataPlaneLocator dstSfDpl, DataPlaneLocator hopDpl) {

        ServiceFunction sfDst = sfcProviderUtils.getServiceFunction(entry.getSf(), entry.getPathId());
        String sfLogicalInterface = SfcLogicalInterfaceOfUtils.getSfLogicalInterface(sfDst);
        LOG.debug("configureTransportEgressFlows:sff->sf egress from a logical sff. "
                + "Target interface:{} si:{}",
                sfLogicalInterface, entry.getServiceIndex());

        // When the SF is using a logical SFF, the transport egress flows are provided by Genius
        Optional<List<Action>> actionList = SfcGeniusRpcClient
                    .getInstance().getEgressActionsFromGeniusRPC(
                            sfLogicalInterface, false);
            if (!actionList.isPresent() || actionList.get().isEmpty()) {
                throw new RuntimeException("Failure during transport egress config. Genius did not return"
                        + " egress actions for logical interface [" + sfLogicalInterface
                        + "] (sf:" + sfDst + ")");
            }
            sfcFlowProgrammer.configureNshEthTransportEgressFlow(
                    sfcProviderUtils.getSffOpenFlowNodeName(entry.getDstSff(), entry.getPathId(), entry.getDstDpnId()),
                    entry.getPathId(), entry.getServiceIndex(), actionList.get());
    }

    /**
     * Configure the Transport Egress flow from an SFF to an SFF / chain
     * egress, depending on the graph entry
     *
     * @param entry - RSP hop info used to create the flow
     * @param srcSffDpl - not used in this processor
     * @param dstSffDpl - not used in this processor
     * @param hopDpl - not used in this processor
     */
    @Override
    public void configureSffTransportEgressFlow(
            SffGraph.SffGraphEntry entry, SffDataPlaneLocator srcSffDpl, SffDataPlaneLocator dstSffDpl, DataPlaneLocator hopDpl) {
        long nsp = entry.getPathId();
        short nsi = entry.getServiceIndex();
        String sffNodeName = sfcProviderUtils.getSffOpenFlowNodeName(entry.getSrcSff(), entry.getPathId(), entry.getSrcDpnId());

        if (entry.getDstSff().equals(SffGraph.EGRESS)) {
            LOG.debug("configureSffTransportEgressFlow: called for chain egress");
            this.sfcFlowProgrammer.configureNshEthLastHopTransportEgressFlow(sffNodeName,nsp,nsi);
        } else {
            LOG.debug("configureSffTransportEgressFlow: called for non-final graph entry");
            if (entry.isIntraLogicalSFFEntry()) {
                // in this case, use Genius to program egress flow
                // 1. Get dpid for both source sff, dst sff
                DpnIdType srcDpid = entry.getSrcDpnId();
                DpnIdType dstDpid = entry.getDstDpnId();
                // 2, use genius to retrieve dst interface name (ITM manager RPC)
                Optional<String> targetInterfaceName = SfcGeniusRpcClient.getInstance()
                        .getTargetInterfaceFromGeniusRPC(srcDpid, dstDpid);
                if (!targetInterfaceName.isPresent()) {
                    throw new RuntimeException("Failure during transport egress config. Genius did not return"
                            + " the interface to use between src dpnid:"
                            + srcDpid + "and dst dpnid:" + dstDpid + ")");
                }

                LOG.debug("configureSffTransportEgressFlow: srcDpn [{}] destDpn [{}] interface to use: [{}]",
                        srcDpid, dstDpid, targetInterfaceName.get());
                // 3, use genius for retrieving egress actions (Interface Manager RPC)
                Optional<List<Action>> actionList = SfcGeniusRpcClient
                        .getInstance().getEgressActionsFromGeniusRPC(
                                targetInterfaceName.get(), true);
                if (!actionList.isPresent() || actionList.get().isEmpty()) {
                    throw new RuntimeException("Failure during transport egress config. Genius did not return"
                            + " egress actions for logical interface [" + targetInterfaceName.get()
                            + "] (src dpnid:" + srcDpid + "; dst dpnid:" + dstDpid + ")");
                }
                // 4, write those actions
                this.sfcFlowProgrammer.configureNshEthTransportEgressFlow(
                        sffNodeName, nsp, nsi, actionList.get());
            }
        }
    }

    @Override
    public void setRspTransports() {
    }

    /** Given a {@link}SfDataPlaneLocator for a SF which uses a logical
     * interface locator, the method returns the SF mac address
     * @param dstSfDpl the data plane locator
     * @return  the optional {@link}MacAddress
     */
    private Optional<MacAddress> getMacAddress(SfDataPlaneLocator dstSfDpl) {
        Optional<MacAddress> theMacAddr = Optional.empty();
        LOG.debug("getMacAddress:starting. dstSfDpl:{}", dstSfDpl);
        String ifName = ((LogicalInterfaceLocator) dstSfDpl.getLocatorType())
                .getInterfaceName();
        theMacAddr = Optional.ofNullable(SfcLogicalInterfaceOfUtils.getServiceFunctionMacAddress(ifName));
        LOG.debug("Read interface's [{}] MAC address [{}]", ifName,
                theMacAddr.isPresent() ? theMacAddr.get().getValue() : "(empty)");
        return theMacAddr;
    }
}