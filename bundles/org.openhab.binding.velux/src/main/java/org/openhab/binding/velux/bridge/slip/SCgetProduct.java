/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.velux.bridge.slip;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.velux.bridge.common.GetProduct;
import org.openhab.binding.velux.bridge.slip.util.KLF200Response;
import org.openhab.binding.velux.bridge.slip.util.Packet;
import org.openhab.binding.velux.things.VeluxKLFAPI.Command;
import org.openhab.binding.velux.things.VeluxKLFAPI.CommandNumber;
import org.openhab.binding.velux.things.VeluxProduct;
import org.openhab.binding.velux.things.VeluxProduct.ProductBridgeIndex;
import org.openhab.binding.velux.things.VeluxProductName;
import org.openhab.binding.velux.things.VeluxProductSerialNo;
import org.openhab.binding.velux.things.VeluxProductType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Protocol specific bridge communication supported by the Velux bridge:
 * <B>Retrieve Product</B>
 * <P>
 * Common Message semantic: Communication with the bridge and (optionally) storing returned information within the class
 * itself.
 * <P>
 * As 3rd level class it defines informations how to send query and receive answer through the
 * {@link org.openhab.binding.velux.bridge.VeluxBridgeProvider VeluxBridgeProvider}
 * as described by the {@link org.openhab.binding.velux.bridge.slip.SlipBridgeCommunicationProtocol
 * SlipBridgeCommunicationProtocol}.
 * <P>
 * Methods in addition to the mentioned interface:
 * <UL>
 * <LI>{@link #setProductId(int)} to define the one specific product.</LI>
 * <LI>{@link #getProduct} to retrieve one specific product.</LI>
 * </UL>
 *
 * @see GetProduct
 * @see SlipBridgeCommunicationProtocol
 *
 * @author Guenther Schreiner - Initial contribution.
 */
@NonNullByDefault
class SCgetProduct extends GetProduct implements SlipBridgeCommunicationProtocol {
    private final Logger logger = LoggerFactory.getLogger(SCgetProduct.class);

    private static final String DESCRIPTION = "Retrieve Product";
    private static final Command COMMAND = Command.GW_GET_NODE_INFORMATION_REQ;

    /*
     * ===========================================================
     * Message Content Parameters
     */

    private int reqNodeID;

    /*
     * ===========================================================
     * Message Objects
     */

    private byte[] requestData = new byte[0];

    /*
     * ===========================================================
     * Result Objects
     */

    private boolean success = false;
    private boolean finished = false;

    private VeluxProduct product = VeluxProduct.UNKNOWN;

    /*
     * ===========================================================
     * Methods required for interface {@link BridgeCommunicationProtocol}.
     */

    @Override
    public String name() {
        return DESCRIPTION;
    }

    @Override
    public CommandNumber getRequestCommand() {
        success = false;
        finished = false;
        logger.debug("getRequestCommand() returns {} ({}).", COMMAND.name(), COMMAND.getCommand());
        return COMMAND.getCommand();
    }

    @Override
    public byte[] getRequestDataAsArrayOfBytes() {
        logger.trace("getRequestDataAsArrayOfBytes() returns data for retrieving node with id {}.", reqNodeID);
        Packet request = new Packet(new byte[1]);
        request.setOneByteValue(0, reqNodeID);
        requestData = request.toByteArray();
        return requestData;
    }

    @Override
    public void setResponse(short responseCommand, byte[] thisResponseData, boolean isSequentialEnforced) {
        KLF200Response.introLogging(logger, responseCommand, thisResponseData);
        success = false;
        finished = false;
        Packet responseData = new Packet(thisResponseData);
        switch (Command.get(responseCommand)) {
            case GW_GET_NODE_INFORMATION_CFM:
                if (!KLF200Response.isLengthValid(logger, responseCommand, thisResponseData, 2)) {
                    finished = true;
                    break;
                }
                int cfmStatus = responseData.getOneByteValue(0);
                int cfmNodeID = responseData.getOneByteValue(1);
                switch (cfmStatus) {
                    case 0:
                        logger.trace("setResponse(): returned status: OK - Request accepted.");
                        if (!KLF200Response.check4matchingNodeID(logger, reqNodeID, cfmNodeID)) {
                            finished = true;
                        }
                        break;
                    case 1:
                        finished = true;
                        logger.trace("setResponse(): returned status: Error – Request rejected.");
                        break;
                    case 2:
                        finished = true;
                        logger.trace("setResponse(): returned status: Error – Invalid node index.");
                        break;
                    default:
                        finished = true;
                        logger.warn("setResponse({}): returned status={} (Reserved/unknown).",
                                Command.get(responseCommand).toString(), cfmStatus);
                        break;
                }
                break;

            case GW_GET_NODE_INFORMATION_NTF:
                finished = true;
                if (!KLF200Response.isLengthValid(logger, responseCommand, thisResponseData, 124)) {
                    break;
                }
                // Extracting information items
                int ntfNodeID = responseData.getOneByteValue(0);
                logger.trace("setResponse(): ntfNodeID={}.", ntfNodeID);
                int ntfOrder = responseData.getTwoByteValue(1);
                logger.trace("setResponse(): ntfOrder={}.", ntfOrder);
                int ntfPlacement = responseData.getOneByteValue(3);
                logger.trace("setResponse(): ntfPlacement={}.", ntfPlacement);
                String ntfName = responseData.getString(4, 64);
                logger.trace("setResponse(): ntfName={}.", ntfName);
                int ntfVelocity = responseData.getOneByteValue(68);
                logger.trace("setResponse(): ntfVelocity={}.", ntfVelocity);
                int ntfNodeTypeSubType = responseData.getTwoByteValue(69);
                logger.trace("setResponse(): ntfNodeTypeSubType={} ({}).", ntfNodeTypeSubType,
                        VeluxProductType.get(ntfNodeTypeSubType));
                logger.trace("setResponse(): derived product description={}.",
                        VeluxProductType.toString(ntfNodeTypeSubType));
                int ntfProductGroup = responseData.getTwoByteValue(71);
                logger.trace("setResponse(): ntfProductGroup={}.", ntfProductGroup);
                int ntfProductType = responseData.getOneByteValue(72);
                logger.trace("setResponse(): ntfProductType={}.", ntfProductType);
                int ntfNodeVariation = responseData.getOneByteValue(73);
                logger.trace("setResponse(): ntfNodeVariation={}.", ntfNodeVariation);
                int ntfPowerMode = responseData.getOneByteValue(74);
                logger.trace("setResponse(): ntfPowerMode={}.", ntfPowerMode);
                int ntfBuildNumber = responseData.getOneByteValue(75);
                logger.trace("setResponse(): ntfBuildNumber={}.", ntfBuildNumber);
                byte[] ntfSerialNumber = responseData.getByteArray(76, 8);
                logger.trace("setResponse(): ntfSerialNumber={}.", ntfSerialNumber);
                int ntfState = responseData.getOneByteValue(84);
                logger.trace("setResponse(): ntfState={}.", ntfState);
                int ntfCurrentPosition = responseData.getTwoByteValue(85);
                logger.trace("setResponse(): ntfCurrentPosition={}.", ntfCurrentPosition);
                int ntfTarget = responseData.getTwoByteValue(87);
                logger.trace("setResponse(): ntfTarget={}.", ntfTarget);
                int ntfFP1CurrentPosition = responseData.getTwoByteValue(89);
                logger.trace("setResponse(): ntfFP1CurrentPosition={}.", ntfFP1CurrentPosition);
                int ntfFP2CurrentPosition = responseData.getTwoByteValue(91);
                logger.trace("setResponse(): ntfFP2CurrentPosition={}.", ntfFP2CurrentPosition);
                int ntfFP3CurrentPosition = responseData.getTwoByteValue(93);
                logger.trace("setResponse(): ntfFP3CurrentPosition={}.", ntfFP3CurrentPosition);
                int ntfFP4CurrentPosition = responseData.getTwoByteValue(95);
                logger.trace("setResponse(): ntfFP4CurrentPosition={}.", ntfFP4CurrentPosition);
                int ntfRemainingTime = responseData.getFourByteValue(97);
                logger.trace("setResponse(): ntfRemainingTime={}.", ntfRemainingTime);
                int ntfTimeStamp = responseData.getFourByteValue(99);
                logger.trace("setResponse(): ntfTimeStamp={}.", ntfTimeStamp);
                int ntfNbrOfAlias = responseData.getOneByteValue(103);
                logger.trace("setResponse(): ntfNbrOfAlias={}.", ntfNbrOfAlias);
                int ntfAliasOne = responseData.getFourByteValue(104);
                logger.trace("setResponse(): ntfAliasOne={}.", ntfAliasOne);
                int ntfAliasTwo = responseData.getFourByteValue(108);
                logger.trace("setResponse(): ntfAliasTwo={}.", ntfAliasTwo);
                int ntfAliasThree = responseData.getFourByteValue(112);
                logger.trace("setResponse(): ntfAliasThree={}.", ntfAliasThree);
                int ntfAliasFour = responseData.getFourByteValue(116);
                logger.trace("setResponse(): ntfAliasFour={}.", ntfAliasFour);
                int ntfAliasFive = responseData.getFourByteValue(120);
                logger.trace("setResponse(): ntfAliasFive={}.", ntfAliasFive);

                if (!KLF200Response.check4matchingNodeID(logger, reqNodeID, ntfNodeID)) {
                    break;
                }

                if (ntfName.length() == 0) {
                    ntfName = "#".concat(String.valueOf(ntfNodeID));
                    logger.debug("setResponse(): device provided invalid name, using '{}' instead.", ntfName);
                }
                String commonSerialNumber = VeluxProductSerialNo.toString(ntfSerialNumber);
                if (VeluxProductSerialNo.isInvalid(ntfSerialNumber)) {
                    commonSerialNumber = new String(ntfName);
                    logger.debug("setResponse(): device provided invalid serial number, using name '{}' instead.",
                            commonSerialNumber);
                }

                product = new VeluxProduct(new VeluxProductName(ntfName), VeluxProductType.get(ntfNodeTypeSubType),
                        new ProductBridgeIndex(ntfNodeID), ntfOrder, ntfPlacement, ntfVelocity, ntfNodeVariation,
                        ntfPowerMode, commonSerialNumber, ntfState, ntfCurrentPosition, ntfTarget, ntfRemainingTime,
                        ntfTimeStamp);
                success = true;
                break;

            default:
                KLF200Response.errorLogging(logger, responseCommand);
        }
        KLF200Response.outroLogging(logger, success, finished);
    }

    @Override
    public boolean isCommunicationFinished() {
        return finished;
    }

    @Override
    public boolean isCommunicationSuccessful() {
        return success;
    }

    /*
     * ===========================================================
     * Methods in addition to the interface {@link BridgeCommunicationProtocol}
     * and the abstract class {@link GetProduct}
     */

    @Override
    public void setProductId(int nodeId) {
        logger.trace("setProductId({}) called.", nodeId);
        this.reqNodeID = nodeId;
        return;
    }

    @Override
    public VeluxProduct getProduct() {
        logger.trace("getProduct(): returning product {}.", product);
        return product;
    }

}
