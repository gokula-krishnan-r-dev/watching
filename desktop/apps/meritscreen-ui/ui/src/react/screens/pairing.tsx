import type { AppBridge } from "../bridge";
import { ErrorState, LoadingState } from "../components/states";
import { Page } from "../components/page";
import { PairingOfferPanel } from "../components/pairing-offer";
import { useChildDetail } from "../lib/use-child-detail";

export function PairingScreen({ bridge, childId }: { bridge: AppBridge; childId: string }) {
  const { detail, loading, error, reload } = useChildDetail(bridge, childId);
  const back = () => void bridge.navigate({ name: "child-detail", childId });
  if (loading && !detail) return <LoadingState />;
  if (error || !detail) return <ErrorState error={error} onRetry={() => void reload()} onBack={back} />;
  return (
    <Page title={`Pair ${detail.card.displayName}’s device`} description="On the child’s computer, open MeritScreen, choose “I’m the child” and enter this code." onBack={back} backLabel={detail.card.displayName} width="md">
      <PairingOfferPanel
        bridge={bridge}
        childId={childId}
        childName={detail.card.displayName}
        onPaired={() => {
          bridge.toast(`${detail.card.displayName}’s device is paired`, "success");
          void bridge.navigate({ name: "child-detail", childId });
        }}
      />
    </Page>
  );
}
