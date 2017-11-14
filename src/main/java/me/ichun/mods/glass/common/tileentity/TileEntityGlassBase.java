package me.ichun.mods.glass.common.tileentity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class TileEntityGlassBase extends TileEntity implements ITickable
{
    public static final HashMap<EnumFacing, ArrayList<EnumFacing>> PROPAGATION_FACES = new HashMap<>();
    static
    {
        for(EnumFacing face : EnumFacing.VALUES)
        {
            ArrayList<EnumFacing> faces = PROPAGATION_FACES.computeIfAbsent(face, v -> new ArrayList<>());
            for(EnumFacing face1 : EnumFacing.VALUES)
            {
                if(!face1.getAxis().equals(face.getAxis()))
                {
                    faces.add(face1);
                }
            }
        }
    }

    public static int FADEOUT_TIME = 12;
    public static int PROPAGATE_TIME = 2;

    public int fadeoutTime = 0;

    public ArrayList<EnumFacing> activeFaces = new ArrayList<>();
    public boolean active = false;
    public String channel = "";
    public int distance = 0; //distance = 0 also means off
    public int propagateTime = 0;

    @Override
    public void update()
    {
        if(fadeoutTime > 0)
        {
            fadeoutTime--;
            if(!active && fadeoutTime == 0)
            {
                activeFaces.clear();
            }
        }
        if(propagateTime > 0)
        {
            propagateTime--;
            if(!world.isRemote && propagateTime == 0)
            {
                propagate();
            }
        }
    }

    public void propagate() //do I need to send active state, channel, online/offline, block change/init propagation?
    {
        //DO STUFF
        HashSet<EnumFacing> propagationFaces = new HashSet<>();
        for(EnumFacing facing : activeFaces)
        {
            propagationFaces.addAll(PROPAGATION_FACES.get(facing));
        }
        for(EnumFacing facing : propagationFaces)
        {
            BlockPos pos = this.getPos().offset(facing);
            TileEntity te = getWorld().getTileEntity(pos);
            if(te instanceof TileEntityGlassBase)
            {
                ((TileEntityGlassBase)te).bePropagatedTo(this, channel, active);
            }
        }
        if(!active)
        {
            channel = "";
            distance = 0;
            IBlockState state = getWorld().getBlockState(getPos());
            getWorld().notifyBlockUpdate(getPos(), state, state, 3);
        }
    }

    public void bePropagatedTo(TileEntityGlassBase base, String newChannel, boolean activate)
    {
        boolean flag = false;
        if(active && activate && channel.equalsIgnoreCase(newChannel)) //same channel and both activated but this is further than the other from master.
        {
            if(distance > base.distance + 1)
            {
                distance = base.distance + 1;
                checkFacesToTurnOn(base);
                flag = true;
            }
        }
        if(activate && !active && (distance > base.distance || distance == 0)) //turn on
        {
            active = true;
            channel = newChannel;
            distance = base.distance + 1;
            checkFacesToTurnOn(base);
            flag = true;
        }
        if(!activate && active && channel.equalsIgnoreCase(newChannel)) //turn off
        {
            if(distance > base.distance || base == this)
            {
                active = false;
                flag = true;
            }
            else
            {
                propagateTime = TileEntityGlassBase.PROPAGATE_TIME + 1;
                IBlockState state = getWorld().getBlockState(getPos());
                getWorld().notifyBlockUpdate(getPos(), state, state, 3);
            }
            //do not set channel or distance as we're still propagating
        }
        if(flag)
        {
            fadeoutTime = TileEntityGlassBase.FADEOUT_TIME;
            propagateTime = TileEntityGlassBase.PROPAGATE_TIME;
            IBlockState state = getWorld().getBlockState(getPos());
            getWorld().notifyBlockUpdate(getPos(), state, state, 3);
        }
    }

    public void checkFacesToTurnOn(TileEntityGlassBase origin)
    {
        if(origin != this)
        {
            activeFaces.clear();
            activeFaces.addAll(origin.activeFaces); //check origin location and remove that active face.
            if(activeFaces.size() > 1)
            {
                for(int i = activeFaces.size() - 1; i >= 0; i--)
                {
                    EnumFacing facing = activeFaces.get(i);
                    BlockPos facePos = getPos().offset(facing, -1);
                    TileEntity te = getWorld().getTileEntity(facePos);
                    if(te instanceof TileEntityGlassBase && ((TileEntityGlassBase)te).distance < distance)
                    {
                        activeFaces.remove(i);
                        continue;
                    }
                    facePos = getPos().offset(facing);
                    te = getWorld().getTileEntity(facePos);
                    if(te instanceof TileEntityGlassBase && ((TileEntityGlassBase)te).distance < distance)
                    {
                        activeFaces.remove(i);
                        continue;
                    }
                }
            }

            HashSet<EnumFacing> newFaces = new HashSet<>();
            for(EnumFacing facing : activeFaces)
            {
                BlockPos facePos = getPos().offset(facing);
                TileEntity te = getWorld().getTileEntity(facePos);
                if(te instanceof TileEntityGlassBase)
                {
                    BlockPos originPos = origin.getPos().offset(facing);
                    EnumFacing newFace = EnumFacing.getFacingFromVector(originPos.getX() - facePos.getX(), originPos.getY() - facePos.getY(), originPos.getZ() - facePos.getZ());
                        newFaces.add(newFace);
                }
                else
                {
                    facePos = getPos().offset(facing, -1);
                    te = getWorld().getTileEntity(facePos);
                    if(te instanceof TileEntityGlassBase)
                    {
                        BlockPos originPos = origin.getPos().offset(facing, -1);
                        EnumFacing newFace = EnumFacing.getFacingFromVector(facePos.getX() - originPos.getX(), facePos.getY() - originPos.getY(), facePos.getZ() - originPos.getZ());
                        if(!newFaces.contains(newFace))
                        {
                            newFaces.add(newFace);
                        }
                    }
                }
            }
            activeFaces.addAll(newFaces);

            IBlockState state = getWorld().getBlockState(getPos());
            getWorld().notifyBlockUpdate(getPos(), state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);
        tag.setInteger("activeFaces", activeFaces.size());
        for(int i = 0; i < activeFaces.size(); i++)
        {
            tag.setInteger("activeFace_" + i, activeFaces.get(i).getIndex());
        }
        tag.setBoolean("active", active);
        tag.setString("channel", channel);
        tag.setInteger("distance", distance);
        tag.setInteger("propagateTime", propagateTime);
        tag.setInteger("fadeoutTime", fadeoutTime);
        return tag;
    }

    @Override
    public NBTTagCompound getUpdateTag()
    {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);
        activeFaces.clear();
        int faceCount = tag.getInteger("activeFaces");
        for(int i = 0; i < faceCount; i++)
        {
            activeFaces.add(EnumFacing.getFront(tag.getInteger("activeFace_" + i)));
        }
        active = tag.getBoolean("active");
        channel = tag.getString("channel");
        distance = tag.getInteger("distance");
        propagateTime = tag.getInteger("propagateTime");
        fadeoutTime = tag.getInteger("fadeoutTime");
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket()
    {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt)
    {
        readFromNBT(pkt.getNbtCompound());
    }
}