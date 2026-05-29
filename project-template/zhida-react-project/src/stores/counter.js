// Zustand 示例代码
import { create } from "zustand"
import { immer } from "zustand/middleware/immer"

export const useCounterStore = create()(
  immer((set) => ({
    count: 0,
    increment: () =>
      set((state) => {
        state.count += 1
      }),
    decrement: () =>
      set((state) => {
        state.count -= 1
      }),
    reset: () => set({ count: 0 }),
  })),
)
