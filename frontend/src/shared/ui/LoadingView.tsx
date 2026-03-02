import { Text, VStack } from '@seed-design/react'

export const LoadingView = ({ message = '불러오는 중...' }: { message?: string }) => (
  <VStack align="center" justify="center" py="x8">
    <Text textStyle="t4Regular" color="fg.neutralSubtle">
      {message}
    </Text>
  </VStack>
)

