package moze_intel.projecte.integration.NEI;

import codechicken.nei.api.API;
import moze_intel.projecte.gameObjs.gui.GUIArcaneTransmutation;

public class NEIInit
{
	public static void init()
	{
		API.registerRecipeHandler(new NEIWorldTransmuteHandler());
		API.registerUsageHandler(new NEIWorldTransmuteHandler());
		API.registerRecipeHandler(new NEIPhiloSmeltingHandler());
		API.registerUsageHandler(new NEIPhiloSmeltingHandler());
		API.registerRecipeHandler(new NEIKleinStarHandler());
		API.registerUsageHandler(new NEIKleinStarHandler());
		API.registerRecipeHandler(new NEIAlchBagHandler());
		API.registerUsageHandler(new NEIAlchBagHandler());

		// 注册奥数转化桌的 NEI ? 号自动填充功能
		API.registerGuiOverlayHandler(GUIArcaneTransmutation.class, new NEIArcaneTransmutationHandler(), "crafting");
		API.registerGuiOverlayHandler(GUIArcaneTransmutation.class, new NEIArcaneTransmutationHandler(), "crafting2x2");
	}
}
