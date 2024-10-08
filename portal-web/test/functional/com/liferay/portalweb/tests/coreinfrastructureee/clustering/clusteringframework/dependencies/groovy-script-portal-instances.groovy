import com.liferay.portal.kernel.instance.PortalInstancePool;

sleep(10000);

long[] companyIds = PortalInstancePool.getCompanyIds();

out.println("Companies count: " + companyIds.length);