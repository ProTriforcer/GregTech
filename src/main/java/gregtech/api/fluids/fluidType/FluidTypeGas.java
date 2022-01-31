package gregtech.api.fluids.fluidType;

import net.minecraftforge.fluids.Fluid;

import javax.annotation.Nonnull;

public class FluidTypeGas extends FluidType {

    private static final String TOOLTIP_NAME = "gregtech.fluid.state_gas";

    public FluidTypeGas(@Nonnull String name, @Nonnull String prefix, @Nonnull String suffix, @Nonnull String localization) {
        super(name, prefix, suffix, localization);
    }

    @Override
    protected void setFluidProperties(@Nonnull Fluid fluid) {
        fluid.setGaseous(true);
        fluid.setDensity(-100);
        fluid.setViscosity(200);
    }

    @Override
    public String getToolTipLocalization() {
        return TOOLTIP_NAME;
    }
}
