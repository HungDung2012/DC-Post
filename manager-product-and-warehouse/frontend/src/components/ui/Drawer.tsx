import { DialogPanel } from './Modal'
import type { DialogProps } from './Modal'

export function Drawer(props: DialogProps) {
  return <DialogPanel {...props} drawer />
}
