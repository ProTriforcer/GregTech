package gregtech.common.metatileentities.multi.electric;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.google.common.collect.Sets;
import gregtech.api.GTValues;
import gregtech.api.capability.*;
import gregtech.api.capability.impl.CleanroomLogic;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.gui.Widget;
import gregtech.api.gui.widgets.AdvancedTextWidget;
import gregtech.api.metatileentity.IDataInfoProvider;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.SimpleGeneratorMetaTileEntity;
import gregtech.api.metatileentity.multiblock.*;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockCleanroomCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MetaTileEntityCleanroom extends MultiblockWithDisplayBase implements ICleanroomProvider, IWorkable, IDataInfoProvider {

    public static final int MIN_DIAMETER = 5;
    public static final int MAX_DIAMETER = 15;
    private int width = 5;
    private int height = 5;
    private int depth = 5;

    private boolean isClean;

    private IEnergyContainer energyContainer;

    private final CleanroomLogic cleanroomLogic;

    public MetaTileEntityCleanroom(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
        this.cleanroomLogic = new CleanroomLogic(this, GTValues.LV);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return new MetaTileEntityCleanroom(metaTileEntityId);
    }

    protected void initializeAbilities() {
        this.energyContainer = new EnergyContainerList(getAbilities(MultiblockAbility.INPUT_ENERGY));
    }

    private void resetTileAbilities() {
        this.energyContainer = new EnergyContainerList(new ArrayList<>());
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        initializeAbilities();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        resetTileAbilities();
        this.isClean = false;
    }

    @Override
    protected void updateFormedValid() {
        this.cleanroomLogic.performDrilling();
        if (!getWorld().isRemote && this.cleanroomLogic.wasActiveAndNeedsUpdate()) {
            this.cleanroomLogic.setWasActiveAndNeedsUpdate(false);
            this.cleanroomLogic.setActive(false);
        }
    }

    @Override
    protected BlockPattern createStructurePattern() {
        // these can sometimes get set to 0 when loading the game, breaking JEI
        if (width == 0)
            width = MIN_DIAMETER;
        if (height == 0)
            height = MIN_DIAMETER;
        if (depth == 0)
            depth = MIN_DIAMETER;

        // build each row of the structure
        StringBuilder border = new StringBuilder("B"); //      BBBBB
        StringBuilder wall = new StringBuilder("B"); //        BXXXB
        StringBuilder inside = new StringBuilder("X"); //      X   X
        StringBuilder roof = new StringBuilder("B"); //        BFFFB
        StringBuilder controller = new StringBuilder("B"); //  BFSFB

        // start with block after left edge, do not include right edge with -1
        for (int i = 1; i < width - 1; i++) {
            border.append("B");
            wall.append("X");
            inside.append(" ");
            roof.append("F");
            if (i == width / 2) controller.append("S"); // controller is always centered
            else controller.append("F");
        }
        border.append("B");
        wall.append("B");
        inside.append("X");
        roof.append("B");
        controller.append("B");


        // build each slice of the structure
        String B = border.toString();
        String W = wall.toString();
        String I = inside.toString();
        String R = roof.toString();
        String C = controller.toString();

        String[] frontBack = new String[height];
        String[] inner = new String[height];
        String[] center = new String[height];

        // bottom and top
        frontBack[0] = B;
        frontBack[height - 1] = B;
        inner[0] = B;
        inner[height - 1] = R;
        center[0] = B;
        center[height - 1] = C;

        // central sections
        for (int i = 1; i < height - 1; i++) {
            frontBack[i] = W;
            inner[i] = I;
            center[i] = I;
        }

        TraceabilityPredicate casing = states(getCasingState()).setMinGlobalLimited(40)
                .or(abilities(MultiblockAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(3))
                .or(autoAbilities());

        // layer the slices one behind the next
        return FactoryBlockPattern.start()
                .aisle(frontBack)
                .aisle(inner).setRepeatable((depth - 3) / 2, (depth - 3) / 2) // excludes controller row, edge rows
                .aisle(center)
                .aisle(inner).setRepeatable((depth - 3) / 2, (depth - 3) / 2)
                .aisle(frontBack)
                .where('S', selfPredicate())
                .where('B', casing)
                .where('X', casing.or(doorPredicate().setMaxGlobalLimited(8).setPreviewCount(0))
                        .or(metaTileEntities(MetaTileEntities.PASSTHROUGH_HATCH_ITEM).setPreviewCount(1))
                        .or(metaTileEntities(MetaTileEntities.PASSTHROUGH_HATCH_FLUID).setPreviewCount(1))
                        .or(metaTileEntities(MetaTileEntities.HULL).setMaxGlobalLimited(5).setPreviewCount(1))
                        .or(metaTileEntities(MetaTileEntities.DIODES).setMaxGlobalLimited(5).setPreviewCount(1)))
                .where('F', states(getFilterState()))
                .where(' ', innerPredicate())
                .build();
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.PLASCRETE;
    }

    // protected to allow easy addition of addon "cleanrooms"
    protected IBlockState getCasingState() {
        return MetaBlocks.CLEANROOM_CASING.getState(BlockCleanroomCasing.CasingType.PLASCRETE);
    }

    // protected to allow easy addition of addon "cleanrooms"
    protected IBlockState getFilterState() {
        return MetaBlocks.CLEANROOM_CASING.getState(BlockCleanroomCasing.CasingType.FILTER_CASING);
    }

    @Nonnull
    protected static TraceabilityPredicate doorPredicate() {
        return new TraceabilityPredicate(blockWorldState -> blockWorldState.getBlockState().getBlock() instanceof BlockDoor);
    }

    // protected to allow easy addition of addon "cleanrooms"
    @Nonnull
    protected TraceabilityPredicate innerPredicate() {
        return new TraceabilityPredicate(blockWorldState -> {
            // all non-MetaTileEntities are allowed inside by default
            TileEntity tileEntity = blockWorldState.getTileEntity();
            if (!(tileEntity instanceof MetaTileEntityHolder))
                return true;

            MetaTileEntity metaTileEntity = ((MetaTileEntityHolder) tileEntity).getMetaTileEntity();

            // blacklisted machines: mufflers, all generators, other cleanrooms
            if (metaTileEntity instanceof IMufflerHatch)
                return false;
            if (metaTileEntity instanceof SimpleGeneratorMetaTileEntity)
                return false;
            if (metaTileEntity instanceof FuelMultiblockController)
                return false;
            if (metaTileEntity instanceof ICleanroomProvider)
                return false;

            // the machine does not need a cleanroom, so do nothing more
            if (!(metaTileEntity instanceof ICleanroomReceiver))
                return true;

            // give the machine this cleanroom
            ICleanroomReceiver cleanroomReceiver = (ICleanroomReceiver) metaTileEntity;
            if (cleanroomReceiver.getCleanroom() == null)
                cleanroomReceiver.setCleanroom(this);
            return true;
        });
    }

    @Override
    protected void addDisplayText(List<ITextComponent> textList) {
        super.addDisplayText(textList);
        textList.add(new TextComponentTranslation("gregtech.multiblock.cleanroom.size", this.width, this.height, this.depth));
        if (!isStructureFormed()) {
            // Width Button
            ITextComponent buttonText = new TextComponentTranslation("gregtech.multiblock.cleanroom.size_modify_width");
            buttonText.appendText(" ");
            buttonText.appendSibling(AdvancedTextWidget.withButton(new TextComponentString("[-]"), "subWidth"));
            buttonText.appendText(" ");
            buttonText.appendSibling(AdvancedTextWidget.withButton(new TextComponentString("[+]"), "addWidth"));
            buttonText.setStyle(new Style().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponentTranslation("gregtech.multiblock.cleanroom.size_explanation")
                            .setStyle(new Style().setColor(TextFormatting.GRAY)))));
            textList.add(buttonText);

            // Height Button
            buttonText = new TextComponentTranslation("gregtech.multiblock.cleanroom.size_modify_height");
            buttonText.appendText(" ");
            buttonText.appendSibling(AdvancedTextWidget.withButton(new TextComponentString("[-]"), "subHeight"));
            buttonText.appendText(" ");
            buttonText.appendSibling(AdvancedTextWidget.withButton(new TextComponentString("[+]"), "addHeight"));
            buttonText.setStyle(new Style().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponentTranslation("gregtech.multiblock.cleanroom.size_explanation")
                            .setStyle(new Style().setColor(TextFormatting.GRAY)))));
            textList.add(buttonText);

            // Depth Button
            buttonText = new TextComponentTranslation("gregtech.multiblock.cleanroom.size_modify_depth");
            buttonText.appendText(" ");
            buttonText.appendSibling(AdvancedTextWidget.withButton(new TextComponentString("[-]"), "subDepth"));
            buttonText.appendText(" ");
            buttonText.appendSibling(AdvancedTextWidget.withButton(new TextComponentString("[+]"), "addDepth"));
            buttonText.setStyle(new Style().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponentTranslation("gregtech.multiblock.cleanroom.size_explanation")
                            .setStyle(new Style().setColor(TextFormatting.GRAY)))));
            textList.add(buttonText);
            return;
        }

        if (energyContainer != null && energyContainer.getEnergyCapacity() > 0) {
            long maxVoltage = Math.max(energyContainer.getInputVoltage(), energyContainer.getOutputVoltage());
            String voltageName = GTValues.VNF[GTUtility.getTierByVoltage(maxVoltage)];
            textList.add(new TextComponentTranslation("gregtech.multiblock.max_energy_per_tick", maxVoltage, voltageName));
        }

        if (!cleanroomLogic.isWorkingEnabled()) {
            textList.add(new TextComponentTranslation("gregtech.multiblock.work_paused"));

        } else if (cleanroomLogic.isActive()) {
            textList.add(new TextComponentTranslation("gregtech.multiblock.running"));
            int currentProgress = getProgressPercent();
            textList.add(new TextComponentTranslation("gregtech.multiblock.progress", currentProgress));
        } else {
            textList.add(new TextComponentTranslation("gregtech.multiblock.idling"));
        }

        if (!drainEnergy(true)) {
            textList.add(new TextComponentTranslation("gregtech.multiblock.not_enough_energy").setStyle(new Style().setColor(TextFormatting.RED)));
        }

        if (isStructureFormed()) {
            if (isClean)
                textList.add(new TextComponentTranslation("gregtech.multiblock.cleanroom.clean_state"));
            else
                textList.add(new TextComponentTranslation("gregtech.multiblock.cleanroom.dirty_state"));
        }
    }

    @Override
    protected void handleDisplayClick(String componentData, Widget.ClickData clickData) {
        super.handleDisplayClick(componentData, clickData);
        switch (componentData) {
            case "subWidth":
                if (withinBounds(width - 2))
                    width -= 2;
                break;
            case "addWidth":
                if (withinBounds(width + 2))
                    width += 2;
                break;
            case "subHeight":
                if (withinBounds(height - 1))
                    height--;
                break;
            case "addHeight":
                if (withinBounds(height + 1))
                    height++;
                break;
            case "subDepth":
                if (withinBounds(depth - 2))
                    depth -= 2;
                break;
            case "addDepth":
                if (withinBounds(depth + 2))
                    depth += 2;
                break;
        }

        reinitializeStructurePattern();
        checkStructurePattern();
        writeCustomData(GregtechDataCodes.UPDATE_STRUCTURE_SIZE, buf -> {
            buf.writeInt(width);
            buf.writeInt(depth);
            buf.writeInt(height);
        });
    }

    protected boolean withinBounds(int size) {
        return size >= MIN_DIAMETER && size <= MAX_DIAMETER;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.2"));
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.3"));
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.4"));
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.5"));
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.6"));
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.7"));
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.8"));
        } else {
            tooltip.add(I18n.format("gregtech.tooltip.hold_shift"));
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), this.cleanroomLogic.isActive(), this.cleanroomLogic.isWorkingEnabled());
    }

    @Nonnull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.CLEANROOM_OVERLAY;
    }

    @Override
    public Set<CleanroomType> getTypes() {
        return Sets.newHashSet(CleanroomType.CLEANROOM);
    }

    @Override
    public void setClean(boolean isClean) {
        this.isClean = isClean;
    }

    @Override
    public boolean isClean() {
        return this.isClean;
    }

    @Nonnull
    @Override
    public List<ITextComponent> getDataInfo() {
        return new ArrayList<>(); //TODO
    }

    @Override
    public boolean isWorkingEnabled() {
        return this.cleanroomLogic.isWorkingEnabled();
    }

    @Override
    public void setWorkingEnabled(boolean isActivationAllowed) {
        if (!isActivationAllowed) // pausing sets not clean
            setClean(false);
        this.cleanroomLogic.setWorkingEnabled(isActivationAllowed);
    }

    @Override
    public int getProgress() {
        return cleanroomLogic.getProgressTime();
    }

    @Override
    public int getMaxProgress() {
        if (getWorld().isRemote)
            return CleanroomLogic.MAX_PROGRESS;
        return cleanroomLogic.getMaxProgress();
    }

    public int getProgressPercent() {
        return (int) (cleanroomLogic.getProgressPercent() * 100);
    }

    @Override
    public int getEnergyTier() {
        if (energyContainer == null)
            return GTValues.LV;

        return Math.max(GTValues.LV, GTUtility.getTierByVoltage(energyContainer.getInputVoltage()));
    }

    @Override
    public long getEnergyInputPerSecond() {
        return energyContainer.getInputPerSec();
    }

    public boolean drainEnergy(boolean simulate) {
        long energyToDrain = GTValues.VA[getEnergyTier()];
        long resultEnergy = energyContainer.getEnergyStored() - energyToDrain;
        if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
            if (!simulate)
                energyContainer.changeEnergy(-energyToDrain);
            return true;
        }
        return false;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_WORKABLE)
            return GregtechTileCapabilities.CAPABILITY_WORKABLE.cast(this);
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE)
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        return super.getCapability(capability, side);
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.UPDATE_STRUCTURE_SIZE) {
            this.width = buf.readInt();
            this.depth = buf.readInt();
            this.height = buf.readInt();
            this.reinitializeStructurePattern();
        }
        this.cleanroomLogic.receiveCustomData(dataId, buf);
    }

    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("width", this.width);
        data.setInteger("depth", this.depth);
        data.setInteger("height", this.height);
        data.setBoolean("isClean", this.isClean);
        return this.cleanroomLogic.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.width = data.hasKey("width") ? data.getInteger("width") : this.width;
        this.depth = data.hasKey("depth") ? data.getInteger("depth") : this.depth;
        this.height = data.hasKey("height") ? data.getInteger("height") : this.height;
        this.isClean = data.getBoolean("isClean");
        this.cleanroomLogic.readFromNBT(data);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.width);
        buf.writeInt(this.depth);
        buf.writeInt(this.height);
        buf.writeBoolean(isClean);
        this.cleanroomLogic.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.width = buf.readInt();
        this.depth = buf.readInt();
        this.height = buf.readInt();
        this.isClean = buf.readBoolean();
        this.cleanroomLogic.receiveInitialSyncData(buf);
    }

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) {
        if (ConfigHolder.machines.enableCleanroom) {
            super.getSubItems(creativeTab, subItems);
        }
    }
}
