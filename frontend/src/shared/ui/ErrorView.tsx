import { ActionButton, Text, VStack } from '@seed-design/react'

interface ErrorViewProps {
  message: string
  onRetry?: () => void
}

export const ErrorView = ({ message, onRetry }: ErrorViewProps) => (
  <VStack gap="x3" align="flex-start" p="x4" borderWidth={1} borderColor="stroke.criticalSubtle" borderRadius="r2">
    <Text textStyle="t5Bold" color="fg.critical">
      오류
    </Text>
    <Text textStyle="t4Regular" color="fg.neutralSubtle">
      {message}
    </Text>
    {onRetry ? (
      <ActionButton variant="neutralWeak" onClick={onRetry}>
        다시 시도
      </ActionButton>
    ) : null}
  </VStack>
)


